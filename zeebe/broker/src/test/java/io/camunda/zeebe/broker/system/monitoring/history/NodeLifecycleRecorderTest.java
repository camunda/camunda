/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NodeLifecycleRecorderTest {

  private final AtomicLong millis =
      new AtomicLong(Instant.parse("2026-11-27T10:00:00Z").toEpochMilli());
  private final InstantSource clock = () -> Instant.ofEpochMilli(millis.getAndAdd(1_000));

  @TempDir private Path directory;

  @Test
  void shouldReportNoPreviousRunOnFirstStart() {
    // when
    final var started = startedEntry(file -> {});

    // then
    assertThat(started.details()).containsEntry("previousShutdown", "NONE");
  }

  @Test
  void shouldReportACleanShutdownEvenWithEntriesRecordedWhileStopping() {
    // when
    final var started =
        startedEntry(
            history -> {
              final var lifecycle = new NodeLifecycleRecorder(history);
              lifecycle.recordStart("8.11.0");
              lifecycle.recordStop();
              history.record("Partition-default-1/Raft", "HEALTH_CHANGED", null, Map.of());
            });

    // then
    assertThat(started.details())
        .containsEntry("previousShutdown", "CLEAN")
        .containsEntry("previousVersion", "8.11.0");
  }

  @Test
  void shouldReportAnUncleanShutdownWhenThePreviousRunNeverStopped() {
    // when
    final var started =
        startedEntry(
            history -> {
              new NodeLifecycleRecorder(history).recordStart("8.10.3");
              history.record("Partition-default-1", "BECAME_LEADER", null, Map.of());
            });

    // then
    assertThat(started.details())
        .containsEntry("previousShutdown", "UNCLEAN")
        .containsEntry("previousVersion", "8.10.3")
        .containsEntry("previousLastEntryAt", "2026-11-27T10:00:01Z");
    assertThat(started.message()).startsWith("started after an unclean shutdown");
  }

  /** Runs the previous run against the history file, then starts again and returns the start. */
  private HealthHistoryEntry startedEntry(final java.util.function.Consumer<HealthHistory> run) {
    final var file = directory.resolve("history.jsonl");
    try (final var previous = new HealthHistory("Broker-0", 100, file, clock)) {
      run.accept(previous);
    }
    try (final var history = new HealthHistory("Broker-0", 100, file, clock)) {
      new NodeLifecycleRecorder(history).recordStart("8.11.1");
      return history.entries().getLast();
    }
  }
}
