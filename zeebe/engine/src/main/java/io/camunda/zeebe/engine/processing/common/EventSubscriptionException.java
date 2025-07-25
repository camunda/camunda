/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Exception that can be thrown during processing of a command, in case the engine could not
 * subscribe to an event. This exception can be handled by the processor in {@link
 * io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor#tryHandleError(TypedRecord,
 * Throwable)}.
 */
public class EventSubscriptionException extends RuntimeException {

  EventSubscriptionException(final String message) {
    super(message);
  }
}
