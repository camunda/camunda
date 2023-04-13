/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
@Disabled("Can't enable async scheduled tasks for this test yet")
final class ScheduledTaskTest {

  private static final VerificationWithTimeout TIMEOUT = timeout(2_000L);

  @SuppressWarnings("unused")
  private StreamPlatform streamPlatform;

  @SuppressWarnings("unused")
  private ControlledActorClock actorClock;

  @Test
  public void shouldBeAbleToSchedulingTaskAsync() throws InterruptedException {
    // given
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    final CountDownLatch asyncServiceLatch = new CountDownLatch(1);
    doAnswer(
            (invocationOnMock) -> {
              final var context = (ReadonlyStreamProcessorContext) invocationOnMock.getArgument(0);
              context
                  .getScheduleService()
                  .runDelayed(
                      Duration.ZERO,
                      (taskResultBuilder) -> {
                        asyncServiceLatch.countDown();
                        return taskResultBuilder.build();
                      });

              return invocationOnMock.callRealMethod();
            })
        .when(mockProcessorLifecycleAware)
        .onRecovered(any());

    // when
    streamPlatform.startStreamProcessor();

    // then
    assertThat(asyncServiceLatch.await(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldBlockProcessingIfSchedulingBlocks() throws InterruptedException {
    // given
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    final CountDownLatch asyncServiceLatch = new CountDownLatch(1);
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    doAnswer(
            (invocationOnMock) -> {
              final var context = (ReadonlyStreamProcessorContext) invocationOnMock.getArgument(0);

              context
                  .getScheduleService()
                  .runAtFixedRate(
                      Duration.ZERO,
                      (taskResultBuilder) -> {
                        try {
                          asyncServiceLatch.countDown();
                          countDownLatch.await();
                        } catch (final InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        return taskResultBuilder.build();
                      });

              return invocationOnMock.callRealMethod();
            })
        .when(mockProcessorLifecycleAware)
        .onRecovered(any());

    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.startStreamProcessor();

    // when
    assertThat(asyncServiceLatch.await(10, TimeUnit.SECONDS)).isTrue();
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultRecordProcessor, timeout(500).times(0)).process(any(), any());
    // free processor
    countDownLatch.countDown();
  }

  @Test
  public void shouldProcessEvenIfAsyncSchedulingBlocks() throws InterruptedException {
    // given
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    final CountDownLatch asyncServiceLatch = new CountDownLatch(1);
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    doAnswer(
            (invocationOnMock) -> {
              final var context = (ReadonlyStreamProcessorContext) invocationOnMock.getArgument(0);

              context
                  .getScheduleService()
                  .runAtFixedRate(
                      Duration.ZERO,
                      (taskResultBuilder) -> {
                        try {
                          asyncServiceLatch.countDown();
                          countDownLatch.await();
                        } catch (final InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        return taskResultBuilder.build();
                      });

              return invocationOnMock.callRealMethod();
            })
        .when(mockProcessorLifecycleAware)
        .onRecovered(any());

    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    streamPlatform.startStreamProcessor();

    // when
    assertThat(asyncServiceLatch.await(10, TimeUnit.SECONDS)).isTrue();
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    verify(defaultRecordProcessor, TIMEOUT.times(1)).process(any(), any());
    // free schedule service
    countDownLatch.countDown();
  }

  @Disabled("Should be enabled when https://github.com/camunda/zeebe/issues/11849 is fixed")
  @Test
  public void shouldRunAsyncSchedulingEvenIfProcessingIsBlocked() throws InterruptedException {
    // given
    final var mockProcessorLifecycleAware = streamPlatform.getMockProcessorLifecycleAware();
    final CountDownLatch asyncServiceLatch = new CountDownLatch(1);
    final CountDownLatch processorLatch = new CountDownLatch(1);
    final CountDownLatch waitLatch = new CountDownLatch(1);
    doAnswer(
            (invocationOnMock) -> {
              final var context = (ReadonlyStreamProcessorContext) invocationOnMock.getArgument(0);

              context
                  .getScheduleService()
                  .runAtFixedRate(
                      Duration.ofMinutes(1),
                      (taskResultBuilder) -> {
                        asyncServiceLatch.countDown();
                        return taskResultBuilder.build();
                      });

              return invocationOnMock.callRealMethod();
            })
        .when(mockProcessorLifecycleAware)
        .onRecovered(any());

    final var defaultRecordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    doAnswer(
            (invocationOnMock -> {
              try {
                processorLatch.countDown();
                waitLatch.await();
              } catch (final InterruptedException e) {
                throw new RuntimeException(e);
              }
              return invocationOnMock.callRealMethod();
            }))
        .when(defaultRecordProcessor)
        .process(any(), any());
    streamPlatform.startStreamProcessor();

    try {
      // when
      streamPlatform.writeBatch(
          RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));
      assertThat(processorLatch.await(5, TimeUnit.SECONDS)).isTrue();

      // then
      await("ProcessScheduleService should still work")
          .timeout(Duration.ofSeconds(5))
          .until(
              () -> {
                actorClock.addTime(Duration.ofMillis(100));
                return asyncServiceLatch.await(100, TimeUnit.MILLISECONDS);
              });
      verify(defaultRecordProcessor, TIMEOUT).process(any(), any());

    } finally {
      // free schedule service
      waitLatch.countDown();
    }
  }
}
