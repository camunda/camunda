/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.storageordinals.TimerStorageOrdinals;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import org.junit.jupiter.api.Test;

final class TimerStorageOrdinalsTest {

  @Test
  void shouldDeriveNotOrdinalControlledForStartEventTimerRegardlessOfPersistedValue() {
    // given: a start event timer row written before the ordinal existed (persisted default 0)
    final var timer = new TimerInstance();
    timer.setElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);
    timer.setStorageOrdinal(0);

    // when / then
    assertThat(TimerStorageOrdinals.of(timer))
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  void shouldUsePersistedOrdinalForInstanceTimer() {
    // given
    final var timer = new TimerInstance();
    timer.setElementInstanceKey(42L);
    timer.setStorageOrdinal(1234);

    // when / then
    assertThat(TimerStorageOrdinals.of(timer)).isEqualTo(1234);
  }

  @Test
  void shouldKeepMainIndexForLegacyInstanceTimer() {
    // given: an instance timer row written before the ordinal existed
    final var timer = new TimerInstance();
    timer.setElementInstanceKey(42L);
    timer.setStorageOrdinal(0);

    // when / then
    assertThat(TimerStorageOrdinals.of(timer)).isEqualTo(StorageOrdinalRelated.MAIN_INDEX);
  }
}
