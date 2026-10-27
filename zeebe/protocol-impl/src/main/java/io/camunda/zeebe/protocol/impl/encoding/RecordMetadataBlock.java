/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.agrona.DirectBuffer;

/**
 * Lightweight decoder that reads only the fixed-length filter fields (recordType, valueType,
 * intent) from SBE-encoded {@link RecordMetadata} without full deserialization. This avoids parsing
 * variable-length fields (rejectionReason, authorization, agent) which are expensive to decode.
 *
 * <p>Usage: call {@link #wrap(DirectBuffer, int)} once, then read individual fields via {@link
 * #recordType()}, {@link #valueType()}, {@link #intent()}.
 *
 * <p>Reusable and allocation-free after construction.
 */
public final class RecordMetadataBlock {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final RecordMetadataDecoder metadataDecoder = new RecordMetadataDecoder();

  /**
   * Wraps the decoder around the SBE-encoded metadata at the given offset. After calling this, the
   * fixed-length fields can be read via {@link #recordType()}, {@link #valueType()}, and {@link
   * #intent()}.
   */
  public void wrap(final DirectBuffer buffer, final int metadataOffset) {
    metadataDecoder.wrapAndApplyHeader(buffer, metadataOffset, headerDecoder);
  }

  public RecordType recordType() {
    return metadataDecoder.recordType();
  }

  public ValueType valueType() {
    return metadataDecoder.valueType();
  }

  public Intent intent() {
    return Intent.fromProtocolValue(metadataDecoder.valueType(), metadataDecoder.intent());
  }
}
