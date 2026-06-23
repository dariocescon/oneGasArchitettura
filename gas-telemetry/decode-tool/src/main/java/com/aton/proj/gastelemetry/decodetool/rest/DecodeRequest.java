package com.aton.proj.gastelemetry.decodetool.rest;

/**
 * Body della richiesta POST /api/decode.
 *
 * @param hex stringa esadecimale del payload TEK822 da decodificare,
 *            eventualmente con spazi. Esempio:
 *            {@code "180203418919360864431047987054047B...2F1D"}
 */
public record DecodeRequest(String hex) {}
