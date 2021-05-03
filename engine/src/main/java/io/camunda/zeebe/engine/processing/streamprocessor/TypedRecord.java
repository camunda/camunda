/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordMetadataEncoder;

public interface TypedRecord<T extends UnifiedRecordValue> extends Record<T> {

  long getKey();

  T getValue();

  int getRequestStreamId();

  long getRequestId();

  long getLength();

  default boolean hasRequestMetadata() {
    return getRequestId() != RecordMetadataEncoder.requestIdNullValue()
        && getRequestStreamId() != RecordMetadataEncoder.requestStreamIdNullValue();
  }
}
