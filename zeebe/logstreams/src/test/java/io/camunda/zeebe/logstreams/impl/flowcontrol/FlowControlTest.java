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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
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
    final var writeContext = new UserCommand(intent);

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

  @Test
  public void shouldRejectEvenInternalWritesWhenPausedForTransfer() {
    // given
    flowControl.pause();

    // when
    final var whilePaused = tryAcquireInternalWrite();

    // then
    EitherAssert.assertThat(whilePaused)
        .isLeft()
        .left()
        .satisfies(r -> assertThat(r).isEqualTo(Rejection.PartitionPaused));

    // and — writes are admitted again after resuming
    flowControl.resume();
    EitherAssert.assertThat(tryAcquireInternalWrite()).isRight();
  }

  private Either<Rejection, InFlightEntry> tryAcquireInternalWrite() {
    return flowControl.tryAcquire(
        WriteContext.internal(),
        List.of(
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.PROCESS_INSTANCE_CREATION)
                    .intent(ProcessInstanceCreationIntent.CREATE),
                new UnifiedRecordValue(0))));
  }

  @Test
  void shouldNotThrottleBeforeFirstExport() {
    // given — a fresh FlowControl (as created on every leader transition) with throttling enabled
    // and a partition that has already written far past the acceptable backlog
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new LogStreamMetricsImpl(meterRegistry);
    final var minRate = 700L;
    final var writeRateLimit =
        new RateLimit(
            true,
            7000,
            Duration.ZERO,
            new Throttling(true, 300_000L, minRate, Duration.ofSeconds(15)));
    final var fc =
        new FlowControl(metrics, StabilizingAIMDLimit.newBuilder().build(), writeRateLimit);
    final var intent = ProcessInstanceCreationIntent.CREATE;
    final var context = new UserCommand(intent);
    final var result =
        fc.tryAcquire(
            context,
            List.of(
                LogAppendEntry.of(
                    new RecordMetadata()
                        .recordType(RecordType.COMMAND)
                        .valueType(ValueType.PROCESS_INSTANCE_CREATION)
                        .intent(intent),
                    new UnifiedRecordValue(0))));
    final var writtenPosition = 4_700_000_000L;
    final var listener = fc.registerEntry(writtenPosition, result.get());

    // when — the first record is written, but nothing has been exported yet
    listener.onWrite(1, writtenPosition);

    // then — the throttle must not engage: with lastExportedPosition still uninitialized, the
    // backlog is unknown, so the write rate must stay at the configured limit rather than being
    // clamped down to minRate. Before the fix, lastExportedPosition defaulted to 0, producing a
    // backlog equal to the entire written position and clamping the rate to minRate.
    assertThat(meterRegistry.get("zeebe.flow.control.write.rate.limit").gauge().value())
        .as("write rate must not be throttled before the first export observation")
        .isNotEqualTo((double) minRate)
        .isEqualTo(7000);
  }

  @Test
  void shouldPublishWriteRateLimitsImmediately() {
    // given — throttling disabled, so RateLimitThrottle never publishes anything
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new LogStreamMetricsImpl(meterRegistry);
    final var writeRateLimit =
        new RateLimit(true, 7000, Duration.ZERO, RateLimit.Throttling.disabled());

    // when — the write rate limit is configured (as happens on construction / via the actuator)
    new FlowControl(metrics, StabilizingAIMDLimit.newBuilder().build(), writeRateLimit);

    // then — the configured limits are published immediately, without waiting for a write or export
    assertThat(meterRegistry.get("zeebe.flow.control.write.rate.maximum").gauge().value())
        .as("max write rate limit is published immediately")
        .isEqualTo(7000);
    assertThat(meterRegistry.get("zeebe.flow.control.write.rate.limit").gauge().value())
        .as("write rate limit is published immediately at the configured limit")
        .isEqualTo(7000);
  }

  @Test
  void shouldRemoveWriteRateMetersWhenWriteRateLimitDisabled() {
    // given — a FlowControl with an enabled write rate limit
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new LogStreamMetricsImpl(meterRegistry);
    final var enabled = new RateLimit(true, 7000, Duration.ZERO, RateLimit.Throttling.disabled());
    final var fc = new FlowControl(metrics, StabilizingAIMDLimit.newBuilder().build(), enabled);
    assertThat(meterRegistry.find("zeebe.flow.control.write.rate.limit").gauge()).isNotNull();
    assertThat(meterRegistry.find("zeebe.flow.control.write.rate.maximum").gauge()).isNotNull();

    // when — the write rate limit is disabled (reconfigured via the actuator)
    fc.setWriteRateLimit(RateLimit.disabled());

    // then — the outdated write rate meters are removed rather than left reporting stale values
    assertThat(meterRegistry.find("zeebe.flow.control.write.rate.limit").gauge()).isNull();
    assertThat(meterRegistry.find("zeebe.flow.control.write.rate.maximum").gauge()).isNull();
  }

  @Test
  void shouldRemoveRequestLimitMeterWhenRequestLimitRemoved() {
    // given — a FlowControl with a request limit
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new LogStreamMetricsImpl(meterRegistry);
    final var fc =
        new FlowControl(metrics, StabilizingAIMDLimit.newBuilder().build(), RateLimit.disabled());
    assertThat(meterRegistry.find("zeebe.backpressure.requests.limit").gauge()).isNotNull();

    // when — the request limit is removed (reconfigured via the actuator)
    fc.setRequestLimit(null);

    // then — the outdated request limit meter is removed rather than left reporting a stale value
    assertThat(meterRegistry.find("zeebe.backpressure.requests.limit").gauge()).isNull();
  }

  @Test
  void shouldNotResetInflightRequestsWhenReconfiguringRequestLimit() {
    // given — a FlowControl with a request limit and one in-flight user command
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new LogStreamMetricsImpl(meterRegistry);
    final var fc =
        new FlowControl(
            metrics,
            StabilizingAIMDLimit.newBuilder().initialLimit(100).build(),
            RateLimit.disabled());
    final var intent = ProcessInstanceCreationIntent.CREATE;
    final var context = new UserCommand(intent);
    final var entry =
        fc.tryAcquire(
                context,
                List.of(
                    LogAppendEntry.of(
                        new RecordMetadata()
                            .recordType(RecordType.COMMAND)
                            .valueType(ValueType.PROCESS_INSTANCE_CREATION)
                            .intent(intent),
                        new UnifiedRecordValue(0))))
            .get();
    fc.registerEntry(1, entry);
    fc.onAppended(entry);
    assertThat(meterRegistry.get("zeebe.backpressure.inflight.requests.count").gauge().value())
        .as("the acquired command is counted as in-flight")
        .isEqualTo(1);

    // when — the request limit is reconfigured (as happens via the actuator)
    fc.setRequestLimit(StabilizingAIMDLimit.newBuilder().initialLimit(200).build());

    // then — the in-flight request count is preserved, not reset by building a new limiter
    assertThat(meterRegistry.get("zeebe.backpressure.inflight.requests.count").gauge().value())
        .as("reconfiguring the request limit must not reset the in-flight request count")
        .isEqualTo(1);
  }

  @Test
  void shouldReduceRequestLimitWhenRingBufferWrapsAround() {
    // given — small ring buffer so wraparound happens quickly
    final var meterRegistry = new SimpleMeterRegistry();
    final var metrics = new LogStreamMetricsImpl(meterRegistry);
    final var requestLimit =
        StabilizingAIMDLimit.newBuilder().initialLimit(100).backoffRatio(0.9).build();
    final var fc = new FlowControl(metrics, requestLimit, RateLimit.disabled(), /* capacity= */ 4);
    final var intent = ProcessInstanceCreationIntent.CREATE;
    final var context = new UserCommand(intent);

    // when — acquire and register more entries than the ring buffer capacity
    // without processing any, so wraparound displaces unprocessed entries
    for (int i = 0; i < 8; i++) {
      final var result =
          fc.tryAcquire(
              context,
              List.of(
                  LogAppendEntry.of(
                      new RecordMetadata()
                          .recordType(RecordType.COMMAND)
                          .valueType(ValueType.PROCESS_INSTANCE_CREATION)
                          .intent(intent),
                      new UnifiedRecordValue(0))));
      if (result.isRight()) {
        fc.registerEntry(i + 1, result.get());
      }
    }

    // then — displaced entries called onDropped, which should reduce the AIMD limit
    // 4 entries are displaced, each calling onDropped which applies backoffRatio (0.9):
    // 100 → 90 → 81 → 72 → 64
    assertThat(requestLimit.getLimit())
        .as("request limit should decrease due to displaced entries calling onDropped")
        .isLessThanOrEqualTo(65);
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
}
