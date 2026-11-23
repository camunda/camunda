/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.exception.UnrecoverableException;

/**
 * Exception type thrown by the {@link ProcessingStateMachine} and {@link ReplayStateMachine} when
 * encountering a record that is not accepted by any of the registered processors, or whose value
 * type this broker does not know.
 */
final class NoSuchProcessorException extends UnrecoverableException {

  private NoSuchProcessorException(final String message) {
    super(message);
  }

  public static NoSuchProcessorException forRecord(final TypedRecord<?> record) {
    final var message =
        switch (record.getRecordType()) {
          case EVENT ->
              "No processor registered for event type %s".formatted(record.getValueType());
          case COMMAND ->
              "No processor registered for command type %s".formatted(record.getValueType());
          case COMMAND_REJECTION, SBE_UNKNOWN, NULL_VAL ->
              ("No processor registered for record type %s").formatted(record.getRecordType());
        };
    return new NoSuchProcessorException(message);
  }

  /**
   * For a record whose value type is unknown to this broker, so its value could not be read and no
   * {@link TypedRecord} exists yet.
   */
  public static NoSuchProcessorException forUnknownValueType(final RecordMetadata metadata) {
    return new NoSuchProcessorException(
        "No processor registered for %s record of unknown value type (broker version %s)"
            .formatted(metadata.getRecordType(), metadata.getBrokerVersion()));
  }
}
