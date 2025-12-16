/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(StreamPlatformExtension.class)
public class StreamProcessorHealthTest {

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @SuppressWarnings("unused") // injected by the extension
  private ControlledActorClock actorClock;

  private StreamProcessor streamProcessor;

  @Test
  void shouldBeHealthyOnStart() {
    // when
    streamProcessor = streamPlatform.startStreamProcessor();

    // then
    Awaitility.await("wait to become healthy again")
        .until(() -> streamProcessor.getHealthReport().isHealthy());
  }

  @Test
  void shouldMarkUnhealthyWhenLoopInErrorHandling() {
    // given
    streamProcessor = streamPlatform.startStreamProcessor();

    final var mockProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(mockProcessor.process(any(), any())).thenThrow(new RuntimeException("expected"));
    when(mockProcessor.onProcessingError(any(), any(), any()))
        .thenThrow(new RuntimeException("expected"));

    // when
    // since processing fails we will write error event
    // we want to fail error even transaction
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

    // then
    Awaitility.await("wait to become unhealthy")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());
  }

  @Test
  void shouldBecomeHealthyWhenErrorIsResolved() {
    // given
    streamProcessor = streamPlatform.startStreamProcessor();
    final var shouldFail = new AtomicBoolean(true);

    final var mockProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    when(mockProcessor.process(any(), any())).thenThrow(new RuntimeException("expected"));
    when(mockProcessor.onProcessingError(any(), any(), any()))
        .thenAnswer(
            invocationOnMock -> {
              if (shouldFail.get()) {
                throw new RuntimeException("expected");
              }
              return EmptyProcessingResult.INSTANCE;
            });
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));
    Awaitility.await("wait to become unhealthy")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());

    // when
    shouldFail.set(false);

    // then
    Awaitility.await("wait to become healthy again")
        .until(() -> streamProcessor.getHealthReport().isHealthy());
  }

  @Test
  void shouldMarkUnhealthyWhenReplayFails() {
    // given
    final AtomicReference<HealthReport> healthReport = new AtomicReference<>();
    streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();
    streamProcessor.addFailureListener(
        new FailureListener() {
          @Override
          public void onFailure(final HealthReport report) {
            healthReport.set(report);
          }

          @Override
          public void onRecovered(final HealthReport report) {
            healthReport.set(report);
          }

          @Override
          public void onUnrecoverableFailure(final HealthReport report) {
            healthReport.set(report);
          }
        });

    final var mockProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    doThrow(new RuntimeException("expected")).when(mockProcessor).replay(any());

    // when
    streamPlatform.writeBatch(
        RecordToWrite.event().processInstance(ELEMENT_ACTIVATED, Records.processInstance(1)));

    // then
    Awaitility.await("wait to become unhealthy")
        .until(() -> healthReport.get() != null && healthReport.get().isUnhealthy());
  }

  @Nested
  final class BlockedActorDetectionTest {

    @BeforeEach
    void setup() {
      // Ensure the clock is behind the real time to trigger blocked detection quickly
      // We must do this before each test to ensure that the health check ticker runs immediately.
      actorClock.setCurrentTime(Instant.ofEpochMilli(1765200166956L));
    }

    @Test
    void shouldDetectBlockedActorDuringProcessing() {
      // given
      streamProcessor = streamPlatform.startStreamProcessor();
      final var mockProcessor = streamPlatform.getDefaultMockedRecordProcessor();
      final var block = new CountDownLatch(1);

      // when - processing blocks indefinitely
      when(mockProcessor.process(any(), any()))
          .thenAnswer(
              invocation -> {
                block.await();
                return EmptyProcessingResult.INSTANCE;
              });

      streamPlatform.writeBatch(
          RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

      // then - should detect a blocked actor and report unhealthy
      try {
        Awaitility.await("wait for blocked actor detection")
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(
                () -> {
                  final var healthReport = streamProcessor.getHealthReport();
                  assertThat(healthReport.isUnhealthy()).isTrue();
                  assertThat(healthReport.getIssue()).isNotNull();
                  assertThat(healthReport.getIssue().message())
                      .startsWith("actor appears blocked, processing")
                      .contains("position=1")
                      .contains("ACTIVATE_ELEMENT");
                });
      } finally {
        block.countDown();
      }
    }

    @Test
    void shouldDetectBlockedActorDuringReplay() {
      // given
      final var mockProcessor = streamPlatform.getDefaultMockedRecordProcessor();
      final var block = new CountDownLatch(1);

      // Set up blocking replay behavior using doAnswer for void method
      Mockito.doAnswer(
              invocation -> {
                block.await();
                return null;
              })
          .when(mockProcessor)
          .replay(any());

      // Write an event before starting replay mode
      streamPlatform.writeBatch(
          RecordToWrite.event().processInstance(ELEMENT_ACTIVATED, Records.processInstance(1)));

      // when - start in replay mode
      streamProcessor = streamPlatform.startStreamProcessorInReplayOnlyMode();

      actorClock.addTime(Duration.ofSeconds(20));

      // then - should detect a blocked actor during replay
      try {
        Awaitility.await("wait for blocked actor detection during replay")
            .atMost(Duration.ofSeconds(15))
            .untilAsserted(
                () ->
                    assertThat(streamProcessor.getHealthReport().getIssue().message())
                        .startsWith("actor appears blocked, replaying event")
                        .contains("ELEMENT_ACTIVATED")
                        .contains("PROCESS_INSTANCE"));
      } finally {
        block.countDown();
      }
    }

    @Test
    void shouldRecoverAfterBlockingOperationCompletes() {
      // given
      streamProcessor = streamPlatform.startStreamProcessor();
      final var mockProcessor = streamPlatform.getDefaultMockedRecordProcessor();
      final var block = new CountDownLatch(1);

      when(mockProcessor.process(any(), any()))
          .thenAnswer(
              invocation -> {
                block.await();
                return EmptyProcessingResult.INSTANCE;
              });

      streamPlatform.writeBatch(
          RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)));

      // when - resetting the clock & unblocking processing after detecting a blocked actor
      try {
        Awaitility.await("wait for blocked actor detection")
            .atMost(Duration.ofSeconds(15))
            .untilAsserted(
                () ->
                    assertThat(streamProcessor.getHealthReport().getIssue().message())
                        .contains("actor appears blocked, processing"));
        actorClock.reset();
      } finally {
        // Unblock processing
        block.countDown();
      }

      // then - should recover and become healthy again
      Awaitility.await("wait for recovery after blocking operation completes")
          .atMost(Duration.ofSeconds(15))
          .untilAsserted(() -> assertThat(streamProcessor.getHealthReport().isHealthy()).isTrue());
    }
  }
}
