/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.message;

import io.camunda.zeebe.engine.common.processing.common.Failure;
import org.agrona.DirectBuffer;

public final class MessageNameException extends RuntimeException {

  private final Failure failure;

  public MessageNameException(final Failure failure, final DirectBuffer failedEventId) {
    super(generateMessage(failedEventId));
    this.failure = failure;
  }

  private static String generateMessage(final DirectBuffer failedEventId) {
    return String.format("Message name could not be resolved for: EventID '%s'", failedEventId);
  }

  public Failure getFailure() {
    return failure;
  }
}
