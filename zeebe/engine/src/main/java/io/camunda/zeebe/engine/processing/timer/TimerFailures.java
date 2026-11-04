/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/** Failures that prevent a timer from being created. */
public final class TimerFailures {

  private static final String DUE_DATE_OUT_OF_RANGE_MESSAGE =
      "Expected the due date of timer '%s' to be within the supported range, but the evaluated "
          + "timer results in a due date that is too far in the past or future.";

  private TimerFailures() {}

  /**
   * Returns the failure for a timer whose due date doesn't fit into epoch milliseconds. {@code
   * scopeKey} is the variable scope that the timer expressions were evaluated in.
   */
  public static Failure dueDateOutOfRange(final DirectBuffer timerId, final long scopeKey) {
    return new Failure(
        DUE_DATE_OUT_OF_RANGE_MESSAGE.formatted(BufferUtil.bufferAsString(timerId)),
        ErrorType.EXTRACT_VALUE_ERROR,
        scopeKey);
  }
}
