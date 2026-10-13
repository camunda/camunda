/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import org.junit.jupiter.api.Test;

final class ProcessDefinitionRestoreContinueProcessorTest {

  @Test
  void shouldProcessEachChunkInASeparateBatch() {
    // given - without this, the self-chained CONTINUE commands would accumulate in one batch
    final var processingState = mock(ProcessingState.class);
    when(processingState.getProcessState()).thenReturn(mock(ProcessState.class));
    final var processor =
        new ProcessDefinitionRestoreContinueProcessor(
            mock(Writers.class, RETURNS_MOCKS), processingState);

    // when / then
    assertThat(processor.shouldProcessResultsInSeparateBatches()).isTrue();
  }
}
