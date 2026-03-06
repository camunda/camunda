/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.impl.LogStreamMetricsImpl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit.Throttling;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.log.WriteContext.UserCommand;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class FlowControlTest {

  @AutoClose MeterRegistry meterRegistry;
  FlowControl flowControl;

  @BeforeEach
  public void setup() {
    meterRegistry = new SimpleMeterRegistry();
    final var logStreamMetrics = new LogStreamMetricsImpl(meterRegistry);
    final var writeRateLimit =
        new RateLimit(
            true, 1, Duration.ofSeconds(10), new Throttling(true, 1L, 0L, Duration.ofSeconds(1)));
    flowControl =
        new FlowControl(
            logStreamMetrics, StabilizingAIMDLimit.newBuilder().build(), writeRateLimit);
  }

  static Stream<Intent> intentClassesProvider() {
    return Intent.INTENT_CLASSES.stream().flatMap(c -> Arrays.stream(c.getEnumConstants()));
  }

  @ParameterizedTest
  @MethodSource("intentClassesProvider")
  public void shouldBlockCommandsBasedOnIntent(final Intent intent) {
    // given
    final var writeContext = new UserCommand(intent, -1, -1);

    // when
    final var permits =
        acquireMultiplePermits(writeContext, writeContext.intent(), ValueType.JOB, 10);

    // then
    assertThat(permits).first().satisfies(first -> EitherAssert.assertThat(first).isRight());
    assertThat(permits.subList(1, permits.size()))
        .allSatisfy(
            permit -> {
              if (WhiteListedCommands.isWhitelisted(intent)) {
                EitherAssert.assertThat(permit).isRight();
              } else {
                EitherAssert.assertThat(permit)
                    .isLeft()
                    .left()
                    .satisfies(r -> assertThat(r).isEqualTo(Rejection.WriteRateLimitExhausted));
              }
            });
  }

  @ParameterizedTest
  @MethodSource("intentClassesProvider")
  public void shouldBlockProcessingResultsBasedOnSourceIntent(final Intent intent) {
    // given
    final var writeContext = WriteContext.processingResult(intent);

    // when
    final var permits = acquireMultiplePermits(writeContext, intent, ValueType.JOB, 10);

    // then
    assertThat(permits).first().satisfies(first -> EitherAssert.assertThat(first).isRight());
    assertThat(permits.subList(1, permits.size()))
        .allSatisfy(
            permit -> {
              if (WhiteListedCommands.isWhitelisted(intent)) {
                EitherAssert.assertThat(permit).isRight();
              } else {
                EitherAssert.assertThat(permit)
                    .isLeft()
                    .left()
                    .satisfies(r -> assertThat(r).isEqualTo(Rejection.WriteRateLimitExhausted));
              }
            });
  }

  @ParameterizedTest
  @MethodSource("intentClassesProvider")
  public void shouldNeverBlockInternalCommands(final Intent intent) {
    // given
    final var writeContext = WriteContext.internal();

    // when
    final var permits = acquireMultiplePermits(writeContext, intent, ValueType.JOB, 10);

    // then
    assertThat(permits).first().satisfies(first -> EitherAssert.assertThat(first).isRight());
    assertThat(permits.subList(1, permits.size()))
        .allSatisfy(permit -> EitherAssert.assertThat(permit).isRight());
  }

  private List<Either<FlowControl.Rejection, InFlightEntry>> acquireMultiplePermits(
      final WriteContext writeContext,
      final Intent intent,
      final ValueType valueType,
      final int n) {
    return IntStream.range(1, n)
        .mapToObj(
            i ->
                flowControl.tryAcquire(
                    writeContext,
                    List.of(
                        LogAppendEntry.of(
                            new RecordMetadata()
                                .recordType(RecordType.COMMAND)
                                .valueType(valueType)
                                .intent(intent),
                            new UnifiedRecordValue(0)))))
        .toList();
  }

  @Nested
  class OnCommitError {

    @Test
    void shouldCleanupInFlightEntryOnCommitError() {
      // given - acquire and register an in-flight entry
      final var writeContext = new UserCommand(JobIntent.COMPLETE, -1, -1);
      final var entry =
          flowControl
              .tryAcquire(
                  writeContext,
                  List.of(
                      LogAppendEntry.of(
                          new RecordMetadata()
                              .recordType(RecordType.COMMAND)
                              .valueType(ValueType.JOB)
                              .intent(JobIntent.COMPLETE),
                          new UnifiedRecordValue(0))))
              .get();
      final long highestPosition = 42L;
      flowControl.registerEntry(highestPosition, entry);
      flowControl.onAppended(entry);

      // when - simulate a commit error (e.g., leader stepping down)
      flowControl.onCommitError(1, highestPosition, new RuntimeException("Leader stepping down"));

      // then - the in-flight entry should be cleaned up and removed
      // Verify by trying to acquire again - if cleanup released the limiter slot, it should work
      final var nextEntry =
          flowControl.tryAcquire(
              writeContext,
              List.of(
                  LogAppendEntry.of(
                      new RecordMetadata()
                          .recordType(RecordType.COMMAND)
                          .valueType(ValueType.JOB)
                          .intent(JobIntent.COMPLETE),
                      new UnifiedRecordValue(0))));
      EitherAssert.assertThat(nextEntry).isRight();
    }

    @Test
    void shouldInvokeCommitErrorHandlerWithRequestMetadata() {
      // given - set up a commit error handler and register a user command entry
      final var capturedRequestId = new AtomicLong(-1);
      final var capturedStreamId = new AtomicInteger(-1);
      final var capturedError = new AtomicReference<Throwable>();
      flowControl.setCommitErrorHandler(
          (requestId, requestStreamId, error) -> {
            capturedRequestId.set(requestId);
            capturedStreamId.set(requestStreamId);
            capturedError.set(error);
          });

      final var writeContext = new UserCommand(JobIntent.COMPLETE, 123L, 1);
      final var entry =
          flowControl
              .tryAcquire(
                  writeContext,
                  List.of(
                      LogAppendEntry.of(
                          new RecordMetadata()
                              .recordType(RecordType.COMMAND)
                              .valueType(ValueType.JOB)
                              .intent(JobIntent.COMPLETE),
                          new UnifiedRecordValue(0))))
              .get();
      final long highestPosition = 42L;
      flowControl.registerEntry(highestPosition, entry);
      flowControl.onAppended(entry);

      // when - simulate a commit error
      final var error = new RuntimeException("Leader stepping down");
      flowControl.onCommitError(1, highestPosition, error);

      // then - the handler should be called with the correct request metadata
      assertThat(capturedRequestId.get()).isEqualTo(123L);
      assertThat(capturedStreamId.get()).isEqualTo(1);
      assertThat(capturedError.get()).isSameAs(error);
    }

    @Test
    void shouldNotInvokeCommitErrorHandlerForNonUserCommandEntries() {
      // given - set up a commit error handler and register an internal entry (no request metadata)
      final var handlerCalled = new AtomicBoolean(false);
      flowControl.setCommitErrorHandler(
          (requestId, requestStreamId, error) -> handlerCalled.set(true));

      final var writeContext = WriteContext.internal();
      final var entry =
          flowControl
              .tryAcquire(
                  writeContext,
                  List.of(
                      LogAppendEntry.of(
                          new RecordMetadata()
                              .recordType(RecordType.COMMAND)
                              .valueType(ValueType.JOB)
                              .intent(JobIntent.COMPLETE),
                          new UnifiedRecordValue(0))))
              .get();
      final long highestPosition = 42L;
      flowControl.registerEntry(highestPosition, entry);
      flowControl.onAppended(entry);

      // when - simulate a commit error
      flowControl.onCommitError(1, highestPosition, new RuntimeException("Leader stepping down"));

      // then - the handler should NOT be called (requestId is -1 for internal entries)
      assertThat(handlerCalled.get()).isFalse();
    }

    @Test
    void shouldHandleCommitErrorForNonExistentEntry() {
      // when - commit error for a position that was never registered
      // then - should not throw
      flowControl.onCommitError(1, 999L, new RuntimeException("Leader stepping down"));
    }
  }
}
