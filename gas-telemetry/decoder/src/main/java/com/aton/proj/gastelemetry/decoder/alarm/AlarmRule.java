package com.aton.proj.gastelemetry.decoder.alarm;

import com.aton.proj.gastelemetry.common.Alarm;
import com.aton.proj.gastelemetry.common.DecoderContext;
import com.aton.proj.gastelemetry.common.Measure;

import java.util.List;

/**
 * Strategia che valuta una <i>condizione di allarme</i> su un batch di misure decodificate
 * di un singolo device.
 *
 * <p>Le regole sono iniettate come {@code List<AlarmRule>} nel decoder: tutte le
 * implementazioni con {@code @Component} vengono auto-collezionate da Spring.
 * Il {@code DecoderContextImpl#publishDecodedData} le invoca in sequenza prima
 * di persistere il pacchetto, accodando gli allarmi prodotti a {@code DecodedPacket.alarms()}.
 *
 * <p>Le regole possono leggere soglie e flag da {@link DecoderContext#getConfig(String, String)}
 * (lookup device → fallback global). I default hardcoded sono usati solo se la chiave
 * non è presente in alcun layer.
 *
 * <p><b>Pattern:</b> regole pure (no I/O, no side effect oltre al return). Un
 * eventuale "alarm rate-limiting" (non emettere lo stesso allarme entro 1 ora)
 * sarà a livello di {@code TimeSeriesService} o di un job di compattazione.
 */
public interface AlarmRule {

    /**
     * @param deviceId IMEI del device proprietario delle misure
     * @param measures misure ricevute nel pacchetto corrente (non modificare)
     * @param ctx      accesso a configurazioni e altre risorse
     * @return lista di allarmi prodotti dalla regola (vuota se nessuna condizione triggerata)
     */
    List<Alarm> evaluate(String deviceId, List<Measure> measures, DecoderContext ctx);
}
