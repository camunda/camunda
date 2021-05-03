/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

/** Convenience class to aggregate all the writers */
public final class Writers {

  private final TypedStreamWriter stream;
  private final StateWriter state;
  private final TypedResponseWriter response;

  public Writers(
      final TypedStreamWriter stream, final StateWriter state, final TypedResponseWriter response) {
    this.stream = stream;
    this.state = state;
    this.response = response;
  }

  /** @return the writer, which is used by the processors to write (follow-up) commands */
  public TypedCommandWriter command() {
    return stream;
  }

  /** @return the writer, which is used by the processors to write command rejections */
  public TypedRejectionWriter rejection() {
    return stream;
  }

  /** @return the writer of events that also changes state for each event it writes */
  public StateWriter state() {
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
