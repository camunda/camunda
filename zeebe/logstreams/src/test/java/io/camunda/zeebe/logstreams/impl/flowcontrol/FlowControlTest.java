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
