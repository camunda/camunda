/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.exception.UnrecoverableException;

/**
 * Exception type for a record that is not accepted by any of the registered processors, or whose
 * value type this broker does not know. It carries the {@link UnprocessableRecord} so that {@link
 * StreamProcessor} can decide from the record's data how to react.
 */
final class NoSuchProcessorException extends UnrecoverableException {

  private final UnprocessableRecord record;

  private NoSuchProcessorException(final String message, final UnprocessableRecord record) {
    super(message);
    this.record = record;
  }

  UnprocessableRecord unprocessableRecord() {
    return record;
  }

  public static NoSuchProcessorException forRecord(final UnprocessableRecord record) {
    final var valueType = record.valueType();
    final var valueTypeDescription =
        valueType == ValueType.NULL_VAL || valueType == ValueType.SBE_UNKNOWN
            ? "unknown value type"
            : "value type " + valueType;
    return new NoSuchProcessorException(
        "No processor registered for %s record at position %d of %s (broker version %s)"
            .formatted(
                record.recordType(),
                record.position(),
                valueTypeDescription,
                record.brokerVersion()),
        record);
  }
}
