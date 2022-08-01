/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;

public interface TypedRecord<T extends UnifiedRecordValue> extends Record<T> {

  @Override
  long getKey();

  @Override
  T getValue();

  int getRequestStreamId();

  long getRequestId();

  int getLength();

  default boolean hasRequestMetadata() {
    return getRequestId() != RecordMetadataEncoder.requestIdNullValue()
        && getRequestStreamId() != RecordMetadataEncoder.requestStreamIdNullValue();
  }
}
