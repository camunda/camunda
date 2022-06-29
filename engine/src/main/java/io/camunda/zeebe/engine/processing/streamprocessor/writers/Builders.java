/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.state.EventApplier;

/** Convenience class to aggregate all the writers */
public final class Builders {

  private final RecordsBuilder stream;
  private final StateBuilder state;
  private final TypedResponseWriter response;

  public Builders(
      final RecordsBuilder recordsBuilder,
      final EventApplier eventApplier,
      final TypedResponseWriter typedResponseWriter) {
    stream = recordsBuilder;
    state = new EventApplyingStateBuilder(recordsBuilder, eventApplier);
    response = typedResponseWriter;
  }

  /**
   * @return the writer, which is used by the processors to write (follow-up) commands
   */
  public CommandsBuilder command() {
    return stream;
  }

  /**
   * @return the writer, which is used by the processors to write command rejections
   */
  public RejectionsBuilder rejection() {
    return stream;
  }

  /**
   * @return the writer of events that also changes state for each event it writes
   */
  public StateBuilder state() {
    return state;
  }

  /**
   * Note: {@code flush()} must not be called on the response writer object. This is done centrally
   *
   * @return the response writer, which is used during processing
   */
  public TypedResponseWriter response() {
    return response;
  }
}
