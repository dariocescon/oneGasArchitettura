package com.aton.proj.gastelemetry.decodetool.rest;

import java.util.List;

/**
 * Body della risposta a POST /api/decode.
 *
 * <p>Per ogni campo dell'header e per ogni misura/allarme sono inclusi i
 * metadati di provenienza ({@code *SourceHex}, {@code *ByteRange}). Per le
 * due bitmask Contact Reason e Alarm/Status sono inclusi anche gli array
 * di label dei singoli bit settati ({@code contactReasonFlags},
 * {@code alarmStatusFlags}), prodotti dal modulo decoder via
 * {@code FlagDecoder}.
 */
public record DecodeResponse(
        String deviceId,
        String deviceIdSourceHex,
        String deviceIdByteRange,
        int messageType,
        String messageTypeSourceHex,
        String messageTypeByteRange,
        int declaredBodyLength,
        String declaredBodyLengthSourceHex,
        String declaredBodyLengthByteRange,
        int totalBytes,
        String contactReasonHex,
        String contactReasonByteRange,
        List<String> contactReasonFlags,
        String alarmStatusHex,
        String alarmStatusByteRange,
        List<String> alarmStatusFlags,
        List<MeasureView> measures,
        List<AlarmView> alarms
) {}
