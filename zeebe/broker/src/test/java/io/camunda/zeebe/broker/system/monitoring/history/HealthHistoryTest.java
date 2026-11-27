/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HealthHistoryTest {

  private final AtomicLong millis =
      new AtomicLong(Instant.parse("2026-11-27T10:00:00Z").toEpochMilli());
  private final InstantSource clock = () -> Instant.ofEpochMilli(millis.getAndAdd(1_000));

  @TempDir private Path directory;

  @Test
  void shouldKeepEntriesAcrossRestarts() {
    // given
    final var file = directory.resolve("history.jsonl");
    try (final var history = new HealthHistory("Broker-0", 100, file, clock)) {
      history.record("Partition-default-1", "BECAME_LEADER", "became leader", Map.of("term", "2"));
    }

    // when
    try (final var reopened = new HealthHistory("Broker-0", 100, file, clock)) {
      reopened.record("", "NODE_STARTED", null, Map.of());

      // then
      assertThat(reopened.entries())
          .extracting(HealthHistoryEntry::sequence, HealthHistoryEntry::component)
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple(0L, "Broker-0/Partition-default-1"),
              org.assertj.core.groups.Tuple.tuple(1L, "Broker-0"));
      assertThat(reopened.entries().getFirst().details()).containsEntry("term", "2");
    }
  }

  @Test
  void shouldKeepOnlyTheNewestEntriesAndBoundTheFile() throws IOException {
    // given
    final var file = directory.resolve("history.jsonl");
    try (final var history = new HealthHistory("Broker-0", 3, file, clock)) {

      // when
      for (int i = 0; i < 10; i++) {
        history.record("", "EVENT", "event " + i, Map.of());
      }

      // then
      assertThat(history.entries())
          .extracting(HealthHistoryEntry::message)
          .containsExactly("event 7", "event 8", "event 9");
    }
    assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).hasSizeLessThan(6);
  }

  @Test
  void shouldSkipALineACrashLeftHalfWritten() throws IOException {
    // given
    final var file = directory.resolve("history.jsonl");
    try (final var history = new HealthHistory("Broker-0", 100, file, clock)) {
      history.record("", "NODE_STARTED", null, Map.of());
    }
    Files.writeString(
        file,
        "{\"sequence\":1,\"timest",
        StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.APPEND);

    // when
    try (final var reopened = new HealthHistory("Broker-0", 100, file, clock)) {

      // then
      assertThat(reopened.entries())
          .extracting(HealthHistoryEntry::type)
          .containsExactly("NODE_STARTED");
    }
  }

  @Test
  void shouldFilterByComponentTypeAndLimit() {
    // given
    try (final var history = new HealthHistory("Broker-0", 100, null, clock)) {
      history.record("Partition-default-1", "BECAME_LEADER", null, Map.of());
      history.record("Partition-default-10", "BECAME_LEADER", null, Map.of());
      history.record("Partition-default-1/Raft", "HEALTH_CHANGED", null, Map.of());
      history.record("Partition-default-1", "BECAME_FOLLOWER", null, Map.of());

      // when
      final var partitionOne = history.query(null, "Broker-0/Partition-default-1", null, 10);
      final var leaders = history.query(null, null, "BECAME_LEADER", 1);

      // then
      assertThat(partitionOne)
          .extracting(HealthHistoryEntry::type)
          .containsExactly("BECAME_LEADER", "HEALTH_CHANGED", "BECAME_FOLLOWER");
      assertThat(leaders)
          .extracting(HealthHistoryEntry::component)
          .containsExactly("Broker-0/Partition-default-10");
    }
  }
}
