/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
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

    // when
    eventAppliers.applyState(1, Intent.UNKNOWN, null, 1);

    // then
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
  }

  @Test
  void shouldNotApplyStateUsingRegisteredApplierForOlderVersion() {
    // given
    eventAppliers.register(Intent.UNKNOWN, 1, mockedApplier);

    // when
    eventAppliers.applyState(1, Intent.UNKNOWN, null, 2);

    // then
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
}
