/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.SideEffectProducer;
import java.util.function.Supplier;

public class ResultBuilderBackedSideEffectWriter implements SideEffectWriter {
  private final Supplier<ProcessingResultBuilder> resultBuilderProvider;

  public ResultBuilderBackedSideEffectWriter(
      final Supplier<ProcessingResultBuilder> resultBuilderProvider) {
    this.resultBuilderProvider = resultBuilderProvider;
  }

  @Override
  public void appendSideEffect(final SideEffectProducer sideEffect) {
    resultBuilderProvider.get().appendPostCommitTask(sideEffect::flush);
  }
}
