/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor.SnapshotFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SnapshotDescriptorTest {

  private static final UUID OLD = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID CURRENT = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final Instant NOON = Instant.parse("2026-10-20T12:00:00Z");

  @Test
  void shouldWriteTheContractsFieldOrder() {
    // given
    final var descriptor = descriptor(OLD, 40, 30, 35);

    // when
    final var json = new String(descriptor.toJson(), StandardCharsets.UTF_8);

    // then
    assertThat(json)
        .isEqualTo(
            "{\"version\":1,\"snapshotId\":\"10-1-40-30-0-abc\",\"index\":10,\"term\":1,"
                + "\"processedPosition\":40,\"exportedPosition\":30,\"maxExportedPosition\":35,"
                + "\"lastFollowupEventPosition\":42,"
                + "\"history\":\"00000000-0000-4000-8000-000000000001\","
                + "\"files\":[{\"name\":\"000001.sst\",\"size\":12},"
                + "{\"name\":\"10-1-40-30-0-abc.checksum\",\"size\":3}],"
                + "\"createdAt\":\"2026-10-20T12:00:00Z\"}");
  }

  @Test
  void shouldReadBackWhatItWroteAndSkipFieldsItDoesNotKnow() throws IOException {
    // given
    final var descriptor = descriptor(OLD, 40, 30, 35);
    final var json =
        new String(descriptor.toJson(), StandardCharsets.UTF_8)
            .replace("\"term\":1,", "\"term\":1,\"later\":{\"a\":1},");

    // when
    final var read = SnapshotDescriptor.fromJson(json.getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(read).isEqualTo(descriptor);
  }

  @Test
  void shouldNameTheFieldThatIsWrong() {
    // given
    final var json =
        new String(descriptor(OLD, 40, 30, 35).toJson(), StandardCharsets.UTF_8)
            .replace("\"size\":12", "\"size\":\"twelve\"");

    // when, then
    assertThatThrownBy(() -> SnapshotDescriptor.fromJson(json.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(InvalidSnapshotDescriptorException.class)
        .hasMessage("Expected files.0.size to be a 64-bit integer, but got \"twelve\"");
  }

  @Test
  void shouldRejectAnotherVersion() {
    // given
    final var json =
        new String(descriptor(OLD, 40, 30, 35).toJson(), StandardCharsets.UTF_8)
            .replace("\"version\":1", "\"version\":2");

    // when, then
    assertThatThrownBy(() -> SnapshotDescriptor.fromJson(json.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(InvalidSnapshotDescriptorException.class)
        .hasMessage("Expected version to be 1, but got 2");
  }

  @Test
  void shouldRequireTheHighestPositionItReliesOn() {
    // when, then
    assertThat(descriptor(OLD, 40, 30, 35).requiredPosition()).isEqualTo(42);
    assertThat(descriptor(OLD, 40, 30, 50).requiredPosition()).isEqualTo(50);
  }

  @Test
  void shouldServeAPathOfItsHistoryUpToTheCheckpoint() {
    // given: the current history took over the old one at index 20, position 45
    final var path =
        List.of(
            new Stretch(OLD, Base.NOTHING, new Base(OLD, 20, 45)),
            new Stretch(CURRENT, new Base(OLD, 20, 45), null));

    // when, then
    assertThat(descriptor(OLD, 40, 30, 35).servesPath(path)).isTrue();
    assertThat(descriptor(OLD, 40, 30, 46).servesPath(path))
        .describedAs("an exporter of the old history was past the checkpoint")
        .isFalse();
    assertThat(descriptor(CURRENT, 400, 300, 350).servesPath(path)).isTrue();
    assertThat(descriptor(UUID.randomUUID(), 40, 30, 35).servesPath(path)).isFalse();
  }

  private static SnapshotDescriptor descriptor(
      final UUID history,
      final long processedPosition,
      final long exportedPosition,
      final long maxExportedPosition) {
    return new SnapshotDescriptor(
        1,
        "10-1-40-30-0-abc",
        10,
        1,
        processedPosition,
        exportedPosition,
        maxExportedPosition,
        processedPosition + 2,
        history,
        List.of(
            new SnapshotFile("000001.sst", 12), new SnapshotFile("10-1-40-30-0-abc.checksum", 3)),
        NOON);
  }
}
