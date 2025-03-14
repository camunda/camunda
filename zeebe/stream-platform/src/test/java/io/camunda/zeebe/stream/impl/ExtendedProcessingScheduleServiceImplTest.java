/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup.ASYNC_PROCESSING;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class ExtendedProcessingScheduleServiceImplTest {
  @Test
  void shouldNotScheduleAsyncIfDisabled() {
    // given
    final var context = mock(AsyncScheduleServiceContext.class);
    final var sync = mock(SimpleProcessingScheduleService.class);

    final var schedulingService = new ExtendedProcessingScheduleServiceImpl(context, sync, false);

    // when
    schedulingService.runDelayed(Duration.ZERO, () -> {});

    // then
    Mockito.verify(sync, Mockito.times(1))
        .runDelayed(Mockito.eq(Duration.ZERO), Mockito.<Runnable>any());
  }

  @Test
  void shouldAlwaysScheduleAsyncIfEnabled() {
    // given
    final var sync = mock(SimpleProcessingScheduleService.class);
    final var async = mock(SimpleProcessingScheduleService.class);
    final var asyncControl = mock(AsyncProcessingScheduleServiceActor.class);
    when(asyncControl.createFuture()).thenReturn(new CompletableActorFuture<>());
    doAnswer(
            invocation -> {
              final var runnable = (Runnable) invocation.getArgument(0);
              runnable.run();
              return null;
            })
        .when(asyncControl)
        .run(Mockito.any());

    final var context = mock(AsyncScheduleServiceContext.class);
    when(context.geAsyncActor(ASYNC_PROCESSING)).thenReturn(asyncControl);
    when(context.getAsyncActorService(ASYNC_PROCESSING)).thenReturn(async);

    final var schedulingService = new ExtendedProcessingScheduleServiceImpl(context, sync, true);

    // when
    schedulingService.runDelayed(Duration.ZERO, () -> {});

    // then
    Mockito.verify(async, Mockito.times(1))
        .runDelayed(Mockito.eq(Duration.ZERO), Mockito.<Runnable>any());
  }
}
