/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.waitstate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer;
import org.junit.jupiter.api.Test;

class WaitStateHandlerBuilderTest {

  private static final String INDEX_NAME = "test-wait-state";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldProduceAddAndRemoveHandlerForEachTransformer() {
    // when
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new JobBasedWaitStateTransformer())
            .build();

    // then — one add + one remove
    assertThat(handlers).hasSize(2);
    assertThat(handlers).hasAtLeastOneElementOfType(WaitStateAddHandler.class);
    assertThat(handlers).hasAtLeastOneElementOfType(WaitStateRemoveHandler.class);
  }

  @Test
  void shouldProduceFourHandlersForTwoTransformers() {
    // when
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new JobBasedWaitStateTransformer())
            .addTransformer(new JobBasedWaitStateTransformer())
            .build();

    // then — two transformers → four handlers
    assertThat(handlers).hasSize(4);
  }

  @Test
  void shouldReturnEmptySetWhenNoTransformersAdded() {
    // when
    final var handlers = WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper).build();

    // then
    assertThat(handlers).isEmpty();
  }

  @Test
  void shouldSetCorrectIndexNameOnHandlers() {
    // when
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new JobBasedWaitStateTransformer())
            .build();

    // then
    handlers.forEach(h -> assertThat(h.getIndexName()).isEqualTo(INDEX_NAME));
  }
}
