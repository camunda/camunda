/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventAppliersTest {

  private EventAppliers eventAppliers;

  @BeforeEach
  void setup() {
    eventAppliers = new EventAppliers(mock(MutableProcessingState.class));
  }

  @Test
  void shouldRegisterApplierForAllIntents() {
    // given
    final var events =
        Intent.INTENT_CLASSES.stream()
            .flatMap(c -> Arrays.stream(c.getEnumConstants()))
            // Heuristic to detect event intents which are generally in past or present tense
            // instead of the imperative used for commands.
            .filter(intent -> intent.name().endsWith("ED") || intent.name().endsWith("ING"))
            // CheckpointIntent is not handled by the engine
            .filter(intent -> !(intent instanceof CheckpointIntent))
            // ProcessInstanceResultIntent is only used for client responses and does not appear on
            // the log
            .filter(intent -> !(intent instanceof ProcessInstanceResultIntent));

    // then
    assertThat(events)
        .allSatisfy(
            intent ->
                assertThat(eventAppliers.getApplierForIntent(intent))
                    .describedAs(
                        "Intent %s.%s has a registered event applier",
                        intent.getClass().getSimpleName(), intent.name())
                    .isNotNull());
  }
}
