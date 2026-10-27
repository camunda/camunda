/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks that the other partitions apply clock changes in the order they were made, even when a
 * change has to be sent again.
 *
 * <p>Each test uses its own engine. The engine plans when to send a change again using the clock
 * itself, so a clock that an earlier test moved would delay this.
 */
public final class ClockDistributionOrderTest {

  private static final int OTHER_PARTITION = 2;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(2);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyAddsInOrderWhenTheFirstIsSentAgain() {
    // given
    dropFirstClockCommandToOtherPartition();
    final var first = engine.clock().add(Duration.ofHours(1));
    final var second = engine.clock().add(Duration.ofMinutes(30));

    // when
    final var applied = resendUntilAppliedOnOtherPartition(first, second);

    // then
    assertThat(applied)
        .describedAs("the clock changes on the other partition, in the order it applied them")
        .extracting(Record::getKey, change -> change.getValue().getTime())
        .containsExactly(
            tuple(first.getKey(), Duration.ofHours(1).toMillis()),
            tuple(second.getKey(), Duration.ofMinutes(90).toMillis()));
    awaitClockOfOtherPartition(Modification.offsetBy(Duration.ofMinutes(90)));
  }

  @Test
  public void shouldApplyPinAndResetInOrderWhenThePinIsSentAgain() {
    // given
    dropFirstClockCommandToOtherPartition();
    final var pin =
        engine
            .clock()
            .pinAt(Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS));
    final var reset = engine.clock().reset();

    // when
    final var applied = resendUntilAppliedOnOtherPartition(pin, reset);

    // then
    assertThat(applied)
        .describedAs("the clock changes on the other partition, in the order it applied them")
        .extracting(Record::getKey, Record::getIntent)
        .containsExactly(
            tuple(pin.getKey(), ClockIntent.PINNED), tuple(reset.getKey(), ClockIntent.RESETTED));
    awaitClockOfOtherPartition(Modification.none());
  }

  private void dropFirstClockCommandToOtherPartition() {
    final var dropped = new AtomicBoolean();
    engine.interceptInterPartitionCommands(
        (receiver, valueType, intent, key, command) ->
            receiver != OTHER_PARTITION
                || valueType != ValueType.CLOCK
                || !dropped.compareAndSet(false, true));
  }

  /**
   * Moves time forward until the other partition has applied all the given clock changes, so that
   * the dropped change is sent again. Returns the events of the other partition for these changes,
   * in the order it applied them.
   */
  @SafeVarargs
  private List<Record<ClockRecordValue>> resendUntilAppliedOnOtherPartition(
      final Record<ClockRecordValue>... changes) {
    final var keys = Arrays.stream(changes).map(Record::getKey).toList();
    final Supplier<List<Record<ClockRecordValue>>> applied =
        () ->
            RecordingExporter.clockRecords()
                .onlyEvents()
                .withPartitionId(OTHER_PARTITION)
                .filter(event -> keys.contains(event.getKey()))
                .limit(keys.size())
                .toList();
    RecordingExporter.await()
        .untilAsserted(
            () -> {
              engine
                  .getClock()
                  .addTime(EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL);
              assertThat(applied.get()).hasSameSizeAs(keys);
            });
    return applied.get();
  }

  private void awaitClockOfOtherPartition(final Modification expected) {
    Awaitility.await("until the clock of the other partition is " + expected)
        .untilAsserted(
            () ->
                assertThat(engine.getStreamClock(OTHER_PARTITION).currentModification())
                    .isEqualTo(expected));
  }
}
