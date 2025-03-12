/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class ExtendedProcessingScheduleServiceImplTest {
  @Test
  void shouldNotScheduleAsyncIfDisabled() {
    // given
    final var sync = mock(ProcessingScheduleServiceImpl.class);
    final var async = mock(ProcessingScheduleServiceImpl.class);
    final var concurrencyControl = mock(AsyncProcessingScheduleServiceActor.class);
    final var schedulingService =
        new ExtendedProcessingScheduleServiceImpl(sync, async, concurrencyControl, false);

    // when
    schedulingService.runDelayed(Duration.ZERO, () -> {});

    // then
    Mockito.verify(sync, Mockito.times(1))
        .runDelayed(Mockito.eq(Duration.ZERO), Mockito.<Runnable>any());
  }

  @Test
  void shouldAlwaysScheduleAsyncIfEnabled() {
    // given
    final var sync = mock(ProcessingScheduleServiceImpl.class);
    final var async = mock(ProcessingScheduleServiceImpl.class);
    final var concurrencyControl = mock(AsyncProcessingScheduleServiceActor.class);
    when(concurrencyControl.createFuture()).thenReturn(new CompletableActorFuture<>());
    doAnswer(
            invocation -> {
              final var runnable = (Runnable) invocation.getArgument(0);
              runnable.run();
              return null;
            })
        .when(concurrencyControl)
        .run(Mockito.any());

    final var schedulingService =
        new ExtendedProcessingScheduleServiceImpl(sync, async, concurrencyControl, true);

    // when
    schedulingService.runDelayed(Duration.ZERO, () -> {});

    // then
    Mockito.verify(async, Mockito.times(1))
        .runDelayed(Mockito.eq(Duration.ZERO), Mockito.<Runnable>any());
  }
}
