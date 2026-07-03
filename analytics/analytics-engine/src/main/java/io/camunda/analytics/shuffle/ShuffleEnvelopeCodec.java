/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import io.camunda.analytics.shuffle.sbe.MessageHeaderDecoder;
import io.camunda.analytics.shuffle.sbe.MessageHeaderEncoder;
import io.camunda.analytics.shuffle.sbe.Operation;
import io.camunda.analytics.shuffle.sbe.PayloadKind;
import io.camunda.analytics.shuffle.sbe.ShuffleEnvelopeDecoder;
import io.camunda.analytics.shuffle.sbe.ShuffleEnvelopeEncoder;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * SBE codec for {@link ShuffleEnvelope}, the analytics shuffle wire format — the same
 * generated-encoder/decoder approach the Event Bridge uses for its own protocol, rather than a
 * hand-rolled frame. Versioning and schema evolution come from the SBE {@code messageHeader}
 * (schemaId + version); a frame that is not this schema/message is rejected. Deterministic:
 * encoding depends only on the envelope's fields.
 */
public final class ShuffleEnvelopeCodec {

  private ShuffleEnvelopeCodec() {}

  public static byte[] encode(final ShuffleEnvelope envelope) {
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final ShuffleEnvelopeEncoder encoder = new ShuffleEnvelopeEncoder();
    encoder
        .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
        .producedAt(envelope.producedAt())
        .schemaVersion(envelope.schemaVersion())
        .aggId(envelope.aggId())
        .windowStart(envelope.windowStart())
        .producerPartition(envelope.producerPartition())
        .segment(envelope.segment())
        .payloadKind(envelope.payloadKind())
        .operation(envelope.operation());
    encoder.putKey(envelope.key(), 0, envelope.key().length);
    encoder.putPayload(envelope.payload(), 0, envelope.payload().length);

    final int length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    final byte[] frame = new byte[length];
    buffer.getBytes(0, frame);
    return frame;
  }

  public static ShuffleEnvelope decode(final byte[] frame) {
    final DirectBuffer buffer = new UnsafeBuffer(frame);
    final MessageHeaderDecoder header = new MessageHeaderDecoder();
    header.wrap(buffer, 0);
    if (header.schemaId() != ShuffleEnvelopeDecoder.SCHEMA_ID
        || header.templateId() != ShuffleEnvelopeDecoder.TEMPLATE_ID) {
      throw new IllegalArgumentException(
          "not an analytics shuffle envelope: schemaId="
              + header.schemaId()
              + " templateId="
              + header.templateId());
    }

    final ShuffleEnvelopeDecoder decoder = new ShuffleEnvelopeDecoder();
    decoder.wrap(buffer, header.encodedLength(), header.blockLength(), header.version());
    final long producedAt = decoder.producedAt();
    final int schemaVersion = decoder.schemaVersion();
    final int aggId = decoder.aggId();
    final long windowStart = decoder.windowStart();
    final int producerPartition = decoder.producerPartition();
    final long segment = decoder.segment();
    final PayloadKind payloadKind = decoder.payloadKind();
    final Operation operation = decoder.operation();
    // variable-length fields must be read in schema order: key, then payload
    final byte[] key = new byte[decoder.keyLength()];
    decoder.getKey(key, 0, key.length);
    final byte[] payload = new byte[decoder.payloadLength()];
    decoder.getPayload(payload, 0, payload.length);

    return new ShuffleEnvelope(
        producedAt,
        schemaVersion,
        aggId,
        key,
        windowStart,
        producerPartition,
        segment,
        payloadKind,
        operation,
        payload);
  }
}
