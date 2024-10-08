/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventAppliersTest {

  private EventAppliers eventAppliers;

  @Mock private TypedEventApplier<Intent, ? extends RecordValue> mockedApplier;
  @Mock private TypedEventApplier<Intent, ? extends RecordValue> anotherMockedApplier;

  @BeforeEach
  void setup() {
    eventAppliers = new EventAppliers();
  }

  @Test
  void shouldApplyStateUsingRegisteredApplier() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);
    eventAppliers.register(intent, 1, mockedApplier);

    // when
    eventAppliers.applyState(1, intent, null, 1);

    // then
    Mockito.verify(mockedApplier).applyState(anyLong(), any());
  }

  @Test
  void shouldNotApplyStateUsingUnregisteredApplier() {
    // given no registered appliers

    // then
    assertThatExceptionOfType(NoSuchEventApplier.NoApplierForIntent.class)
        .isThrownBy(() -> eventAppliers.applyState(1, Intent.UNKNOWN, null, 1));
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
  }

  @Test
  void shouldNotApplyStateUsingRegisteredApplierForOlderVersion() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    // when
    eventAppliers.register(intent, 1, mockedApplier);

    // then
    assertThatExceptionOfType(NoSuchEventApplier.NoApplierForVersion.class)
        .isThrownBy(() -> eventAppliers.applyState(1, intent, null, 2));
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
  }

  @Test
  void shouldApplyStateUsingRegisteredApplierForSpecificVersion() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);
    eventAppliers.register(intent, 1, mockedApplier);
    eventAppliers.register(intent, 2, anotherMockedApplier);

    // when
    eventAppliers.applyState(1, intent, null, 2);

    // then
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
    Mockito.verify(anotherMockedApplier).applyState(anyLong(), any());
  }

  @Test
  void shouldGetLatestVersionOfOnlyRegisteredVersion() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    final var expectedVersion = 1;
    eventAppliers.register(intent, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(intent);

    // then
    Assertions.assertEquals(expectedVersion, actualVersion);
  }

  @Test
  void shouldGetLatestVersionOfTwoRegisteredVersions() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    final var expectedVersion = 2;
    eventAppliers.register(intent, 1, mockedApplier);
    eventAppliers.register(intent, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(intent);

    // then
    Assertions.assertEquals(expectedVersion, actualVersion);
  }

  @Test
  void shouldGetLatestVersionMinusOneWhenNoRegisteredVersion() {
    // given
    final var expectedVersion = -1;

    // when
    final var actualVersion = eventAppliers.getLatestVersion(Intent.UNKNOWN);

    // then
    Assertions.assertEquals(expectedVersion, actualVersion);
  }

  @Test
  void shouldGetLatestVersionWhenMultipleRegisteredEventAppliersWithDifferentIntents() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);
    final var expectedVersion = 1;
    eventAppliers.register(intent, expectedVersion, mockedApplier);
    eventAppliers.register(ProcessIntent.CREATED, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(intent);

    // then
    Assertions.assertEquals(expectedVersion, actualVersion);
  }

  @Test
  void shouldRegisterApplierForAllIntents() {
    // given
    final var events =
        Intent.INTENT_CLASSES.stream()
            .flatMap(c -> Arrays.stream(c.getEnumConstants()))
            .filter(Intent::isEvent)
            // CheckpointIntent is not handled by the engine
            .filter(intent -> !(intent instanceof CheckpointIntent))
            // todo delete this filter after all the appliers are implemented
            .filter(intent -> !(intent instanceof RoleIntent))
            .filter(intent -> !(intent instanceof TenantIntent));

    // when
    eventAppliers.registerEventAppliers(mock(MutableProcessingState.class));

    // then
    assertThat(events)
        .allSatisfy(
            intent ->
                assertThat(eventAppliers.getLatestVersion(intent))
                    .describedAs(
                        "Intent %s.%s has a registered event applier",
                        intent.getClass().getSimpleName(), intent.name())
                    .isNotEqualTo(-1));
  }

  @Test
  void cannotRegisterNullApplier() {
    // given
    final var intent = mock(Intent.class);

    // then
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> eventAppliers.register(intent, 1, null));
  }

  @Test
  void cannotRegisterApplierForNullIntent() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> eventAppliers.register(null, 1, mockedApplier));
  }

  @Test
  void cannotRegisterApplierForNegativeVersion() {
    // given
    final var intent = mock(Intent.class);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(intent, -1, mockedApplier));
  }

  @Test
  void cannotRegisterApplierForNonEvent() {
    // given
    final var nonEvent = mock(Intent.class);

    // when
    when(nonEvent.isEvent()).thenReturn(false);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(nonEvent, 1, mockedApplier));
  }

  @Test
  void cannotOverrideApplierForSameIntentAndVersion() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    // when
    eventAppliers.register(intent, 1, mockedApplier);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(intent, 1, anotherMockedApplier));
  }

  @Test
  void shouldOnlyRegisterAppliersForEvents() {
    // given
    final var intents =
        Intent.INTENT_CLASSES.stream()
            .flatMap(c -> Arrays.stream(c.getEnumConstants()))
            // CheckpointIntent is not handled by the engine
            .filter(intent -> !(intent instanceof CheckpointIntent));

    // when
    eventAppliers.registerEventAppliers(mock(MutableProcessingState.class));

    // then
    assertThat(intents)
        .allSatisfy(
            intent -> {
              if (!intent.isEvent()) {
                assertThat(eventAppliers.getLatestVersion(intent))
                    .describedAs(
                        "Intent %s.%s is not an event but has a registered event applier",
                        intent.getClass().getSimpleName(), intent.name())
                    .isEqualTo(-1);
              }
            });
  }
}
