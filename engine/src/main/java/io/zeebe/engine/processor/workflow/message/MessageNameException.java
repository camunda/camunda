/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import static java.util.stream.Collectors.joining;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public final class MessageNameException extends RuntimeException {

  private final BpmnStepContext context;

  public MessageNameException(
      final BpmnStepContext context, final List<DirectBuffer> failedEventIds) {
    super(generateMessage(failedEventIds));
    this.context = context;
  }

  private static String generateMessage(final List<DirectBuffer> failedEventIds) {
    return failedEventIds.stream()
        .map(BufferUtil::bufferAsString)
        .collect(joining(", ", "Message name could not be resolved for: EventIDs [", "]"));
  }

  public BpmnStepContext getContext() {
    return context;
  }
}
