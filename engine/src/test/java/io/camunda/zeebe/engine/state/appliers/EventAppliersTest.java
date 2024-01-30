/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
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
    eventAppliers.register(Intent.UNKNOWN, 1, mockedApplier);

    // when
    eventAppliers.applyState(1, Intent.UNKNOWN, null, 1);

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
    eventAppliers.register(Intent.UNKNOWN, 1, mockedApplier);

    // then
    assertThatExceptionOfType(NoSuchEventApplier.NoApplierForVersion.class)
        .isThrownBy(() -> eventAppliers.applyState(1, Intent.UNKNOWN, null, 2));
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
  }

  @Test
  void shouldApplyStateUsingRegisteredApplierForSpecificVersion() {
    // given
    eventAppliers.register(Intent.UNKNOWN, 1, mockedApplier);
    eventAppliers.register(Intent.UNKNOWN, 2, anotherMockedApplier);

    // when
    eventAppliers.applyState(1, Intent.UNKNOWN, null, 2);

    // then
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
    Mockito.verify(anotherMockedApplier).applyState(anyLong(), any());
  }

  @Test
  void shouldGetLatestVersionOfOnlyRegisteredVersion() {
    // given
    final var expectedVersion = 1;
    eventAppliers.register(Intent.UNKNOWN, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(Intent.UNKNOWN);

    // then
    Assertions.assertEquals(expectedVersion, actualVersion);
  }

  @Test
  void shouldGetLatestVersionOfTwoRegisteredVersions() {
    // given
    final var expectedVersion = 2;
    eventAppliers.register(Intent.UNKNOWN, 1, mockedApplier);
    eventAppliers.register(Intent.UNKNOWN, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(Intent.UNKNOWN);

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
    final var expectedVersion = 1;
    eventAppliers.register(Intent.UNKNOWN, expectedVersion, mockedApplier);
    eventAppliers.register(ProcessIntent.CREATED, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(Intent.UNKNOWN);

    // then
    Assertions.assertEquals(expectedVersion, actualVersion);
  }
}
