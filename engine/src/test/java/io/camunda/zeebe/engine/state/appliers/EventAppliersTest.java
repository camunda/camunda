/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventAppliersTest {

  private EventAppliers eventAppliers;
  @Mock private TypedEventApplier<Intent, ? extends RecordValue> mockedApplier;
  @Mock private TypedEventApplier<Intent, ? extends RecordValue> anotherMockedApplier;

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
            .filter(Intent::isEvent)
            // CheckpointIntent is not handled by the engine
            .filter(intent -> !(intent instanceof CheckpointIntent));

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

  @Test
  void cannotRegisterNullApplier() {
    // given
    final var intent = mock(Intent.class);

    // then
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> eventAppliers.register(intent, null));
  }

  @Test
  void cannotRegisterApplierForNullIntent() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> eventAppliers.register(null, mockedApplier));
  }

  @Test
  void cannotRegisterApplierForNegativeVersion() {
    // given
    final var intent = mock(Intent.class);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(intent, mockedApplier));
  }

  @Test
  void cannotRegisterApplierForNonEvent() {
    // given
    final var nonEvent = mock(Intent.class);

    // when
    when(nonEvent.isEvent()).thenReturn(false);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(nonEvent, mockedApplier));
  }

  @Test
  void cannotOverrideApplierForSameIntent() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    // when
    eventAppliers.register(intent, mockedApplier);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(intent, anotherMockedApplier));
  }

  @Test
  void shouldOnlyRegisterAppliersForEvents() {
    // given
    final var intents =
        Intent.INTENT_CLASSES.stream()
            .flatMap(c -> Arrays.stream(c.getEnumConstants()))
            // CheckpointIntent is not handled by the engine
            .filter(intent -> !(intent instanceof CheckpointIntent));

    // then
    assertThat(intents)
        .allSatisfy(
            intent -> {
              if (!intent.isEvent()) {
                assertThat(eventAppliers.getApplierForIntent(intent))
                    .describedAs(
                        "Intent %s.%s is not an event but has a registered event applier",
                        intent.getClass().getSimpleName(), intent.name())
                    .isNull();
              }
            });
  }
}
