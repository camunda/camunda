/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.dispatch;

import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class DispatcherTest {

  @Test
  void testRescheduling() {
    // given
    final ArgumentCaptor<Long> positionUpdaterCaptor = ArgumentCaptor.captor();
    final Consumer<Long> positionUpdater = Mockito.mock();
    final var dispatcher = new DispatcherImpl(1);
    final var atomicBoolean = new java.util.concurrent.atomic.AtomicBoolean(true);

    // when
    dispatcher.dispatch(() -> positionUpdater.accept(1L));

    dispatcher.dispatch(
        () -> {
          if (atomicBoolean.get()) {
            atomicBoolean.set(false);
            throw new RuntimeException("Simulated dispatch failure");
          }
          positionUpdater.accept(2L);
        });

    dispatcher.dispatch(() -> positionUpdater.accept(3L));

    Awaitility.await().until(() -> !dispatcher.isActive());

    // then
    Mockito.verify(positionUpdater, Mockito.times(3)).accept(positionUpdaterCaptor.capture());
    final var capturedPositions = positionUpdaterCaptor.getAllValues();
    org.assertj.core.api.Assertions.assertThat(capturedPositions).containsExactly(1L, 2L, 3L);

    dispatcher.close();
  }
}
