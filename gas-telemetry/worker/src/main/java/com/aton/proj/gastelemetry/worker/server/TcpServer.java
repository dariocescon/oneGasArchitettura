package com.aton.proj.gastelemetry.worker.server;

import com.aton.proj.gastelemetry.common.Worker;
import com.aton.proj.gastelemetry.worker.context.WorkerContextImpl;
import com.aton.proj.gastelemetry.worker.persistence.CommandService;
import com.aton.proj.gastelemetry.worker.publisher.PrimaryDataPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Server TCP che accetta connessioni dai device, legge il payload grezzo
 * e delega l'elaborazione a Worker.doWork().
 *
 * Usa virtual threads (Java 21) per gestire fino a maxConnections sessioni
 * concorrenti senza overhead di thread OS.
 */
@Component
public class TcpServer {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    /** Lunghezza dell'header fisso TEK822 (byte 0..16 inclusi). */
    static final int HEADER_SIZE = 17;

    private final Worker worker;
    private final PrimaryDataPublisher publisher;
    private final CommandService commandService;

    @Value("${worker.tcp.port:8091}")
    private int port;

    @Value("${worker.tcp.max-connections:1000}")
    private int maxConnections;

    /** Timeout (ms) applicato a ogni read sul socket. */
    @Value("${worker.tcp.read-timeout-ms:30000}")
    private int readTimeoutMs;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private Semaphore connectionSemaphore;

    public TcpServer(Worker worker,
                     PrimaryDataPublisher publisher,
                     CommandService commandService) {
        this.worker = worker;
        this.publisher = publisher;
        this.commandService = commandService;
    }

    @PostConstruct
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        connectionSemaphore = new Semaphore(maxConnections);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.submit(this::acceptLoop);
        log.info("TCP server listening on port {}", port);
    }

    @PreDestroy
    public void stop() throws IOException {
        serverSocket.close();
        executor.shutdown();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                if (!connectionSemaphore.tryAcquire()) {
                    log.warn("Max connections reached, rejecting {}", socket.getRemoteSocketAddress());
                    socket.close();
                    continue;
                }
                executor.submit(() -> handleConnection(socket));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log.error("Accept error", e);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        String clientAddress = socket.getRemoteSocketAddress().toString();
        try (socket) {
            socket.setSoTimeout(readTimeoutMs);
            log.debug("New connection from {}", clientAddress);

            byte[] data = readFramedMessage(socket.getInputStream());

            if (!worker.validate(data)) {
                log.warn("Invalid packet from {} ({} byte), discarding", clientAddress, data.length);
                return;
            }

            // WorkerContextImpl riceve l'OutputStream per rispondere al device
            // e i bean infrastrutturali (PrimaryDataPublisher, CommandService).
            WorkerContextImpl ctx = new WorkerContextImpl(
                    socket.getOutputStream(),
                    publisher,
                    commandService
            );

            worker.doWork(ctx, data);

        } catch (SocketTimeoutException e) {
            log.warn("Read timeout dopo {}ms da {}: {}", readTimeoutMs, clientAddress, e.getMessage());
        } catch (IOException e) {
            log.error("I/O error gestendo connessione da {}: {}", clientAddress, e.getMessage());
        } catch (Exception e) {
            log.error("Errore inaspettato gestendo connessione da {}", clientAddress, e);
        } finally {
            connectionSemaphore.release();
        }
    }

    /**
     * Legge un messaggio TEK822 completo dal socket in 2 fasi:
     * <ol>
     *   <li>header fisso di {@value #HEADER_SIZE} byte</li>
     *   <li>body di lunghezza dichiarata nell'header (campo 10-bit sui byte 15-16)</li>
     * </ol>
     *
     * Restituisce header+body concatenati come un unico array.
     *
     * <p>Usa {@link #readExactly(InputStream, int)} per garantire la lettura completa
     * anche se TCP consegna i dati in più segmenti — comune su reti NB-IoT/CAT-M1.
     *
     * <p>Logica portata da: {@code onGas_Meteor_claude} → {@code TcpConnectionHandlerReadExactly.readData()}.
     *
     * <p>Package-private per consentire i test diretti del framing senza istanziare il server.
     */
    static byte[] readFramedMessage(InputStream in) throws IOException {
        // Fase 1: leggi esattamente 17 byte di header
        byte[] header = readExactly(in, HEADER_SIZE);

        // Fase 2: estrai la lunghezza del body dall'header.
        // Campo 10-bit: bit[7:6] di byte 15 (high 2 bit) + byte 16 (low 8 bit) → max 1023 byte
        int declaredLength = ((header[15] >> 6) & 0x03) * 256 + (header[16] & 0xFF);
        log.debug("Header letto — body dichiarato: {} byte", declaredLength);

        if (declaredLength == 0) {
            // Niente body — il messaggio è solo l'header (raro ma valido)
            return header;
        }

        // Fase 3: leggi esattamente declaredLength byte di body
        byte[] body = readExactly(in, declaredLength);

        // Fase 4: concatena header + body
        byte[] result = new byte[HEADER_SIZE + declaredLength];
        System.arraycopy(header, 0, result, 0, HEADER_SIZE);
        System.arraycopy(body, 0, result, HEADER_SIZE, declaredLength);

        log.debug("Messaggio completo letto: {} byte totali", result.length);
        return result;
    }

    /**
     * Legge esattamente {@code n} byte dall'InputStream, bloccando in loop fino al completamento.
     *
     * <p>{@link InputStream#read(byte[], int, int)} può ritornare meno byte di quelli richiesti
     * (TCP frammenta i pacchetti); questa utility riassembla la lettura.
     *
     * @throws IOException se lo stream viene chiuso prima che N byte siano stati letti
     */
    static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int offset = 0;
        while (offset < n) {
            int read = in.read(buf, offset, n - offset);
            if (read == -1) {
                throw new IOException("Stream chiuso dopo " + offset + " di " + n + " byte attesi");
            }
            offset += read;
        }
        return buf;
    }
}
