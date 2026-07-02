/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.Task;

/**
 * Adapts an analytics-engine {@link StreamProcessor} to the runtime {@link Task} SPI. The
 * lifecycles line up one-to-one, so this is the single seam between the (transport-agnostic)
 * processing engine and the (analytics-agnostic) stream runtime.
 *
 * @param <R> the record type the processor consumes
 */
final class StreamProcessorTask<R> implements Task<R> {

  private final StreamProcessor<R> processor;

  StreamProcessorTask(final StreamProcessor<R> processor) {
    this.processor = processor;
  }

  @Override
  public void init() {
    processor.init();
  }

  @Override
  public void process(final R record) {
    processor.process(record);
  }

  @Override
  public void flush() {
    processor.flush();
  }

  @Override
  public void checkpoint() {
    processor.checkpoint();
  }

  @Override
  public void close() {
    processor.close();
  }
}
