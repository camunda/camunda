/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record;

import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import org.agrona.DirectBuffer;

/**
 * Lightweight decoder that reads only the RecordType from SBE-encoded metadata without full
 * deserialization. Reusable and allocation-free after construction.
 */
public final class RecordMetadataRecordTypeDecoder {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final RecordMetadataDecoder metadataDecoder = new RecordMetadataDecoder();

  public RecordType getRecordType(final DirectBuffer buffer, final int metadataOffset) {
    return metadataDecoder.wrapAndApplyHeader(buffer, metadataOffset, headerDecoder).recordType();
  }
}
