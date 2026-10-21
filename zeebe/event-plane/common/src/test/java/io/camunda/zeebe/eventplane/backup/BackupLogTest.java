/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BackupLogTest {

  private static final UUID HISTORY = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final Instant NOON = Instant.parse("2026-10-21T12:00:00Z");

  @Test
  void shouldWriteTheContractsFieldOrder() {
    // given
    final var backupLog = new BackupLog(1, 17, 1052032, HISTORY, 1090001, 1096704, NOON);

    // when
    final var json = new String(backupLog.toJson(), StandardCharsets.UTF_8);

    // then
    assertThat(json)
        .isEqualTo(
            "{\"version\":1,\"checkpointId\":17,\"checkpointPosition\":1052032,"
                + "\"history\":\"00000000-0000-4000-8000-000000000001\","
                + "\"firstIndex\":1090001,\"lastIndex\":1096704,"
                + "\"createdAt\":\"2026-10-21T12:00:00Z\"}");
  }

  @Test
  void shouldReadBackWhatItWroteAndSkipFieldsItDoesNotKnow() throws IOException {
    // given
    final var backupLog = new BackupLog(1, 17, 1052032, HISTORY, 1090001, 1096704, NOON);
    final var json =
        new String(backupLog.toJson(), StandardCharsets.UTF_8)
            .replace("\"history\"", "\"later\":[1,2],\"history\"");

    // when
    final var read = BackupLog.fromJson(json.getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(read).isEqualTo(backupLog);
  }

  @Test
  void shouldNeedTheEntriesFromItsFirstToItsLastIndex() {
    // given
    final var backupLog = new BackupLog(1, 17, 1052032, HISTORY, 10, 20, NOON);

    // then
    assertThat(backupLog.needs(9)).isFalse();
    assertThat(backupLog.needs(10)).isTrue();
    assertThat(backupLog.needs(20)).isTrue();
    assertThat(backupLog.needs(21)).isFalse();
  }

  @Test
  void shouldRejectALastIndexBeforeTheFirst() {
    assertThatThrownBy(() -> new BackupLog(1, 17, 1052032, HISTORY, 20, 10, NOON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected a first index of 1 or more, and a last index at or after it, but got 20 and 10");
  }

  @Test
  void shouldRejectAnotherVersion() {
    // given
    final var json =
        new String(
                new BackupLog(1, 17, 1052032, HISTORY, 10, 20, NOON).toJson(),
                StandardCharsets.UTF_8)
            .replace("\"version\":1", "\"version\":2");

    // then
    assertThatThrownBy(() -> BackupLog.fromJson(json.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(InvalidBackupLogException.class)
        .hasMessage("Expected version to be 1, but got 2");
  }

  @Test
  void shouldRejectAMissingHistory() {
    // given
    final var json =
        "{\"version\":1,\"checkpointId\":17,\"checkpointPosition\":5,\"firstIndex\":1,"
            + "\"lastIndex\":2,\"createdAt\":\"2026-10-21T12:00:00Z\"}";

    // then
    assertThatThrownBy(() -> BackupLog.fromJson(json.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(InvalidBackupLogException.class)
        .hasMessage("Expected history to be a UUID, but got nothing")
        .extracting(e -> ((InvalidBackupLogException) e).field())
        .isEqualTo("history");
  }

  @Test
  void shouldRejectFieldsThatBreakItsRules() {
    // given
    final var json =
        "{\"version\":1,\"checkpointId\":17,\"checkpointPosition\":0,"
            + "\"history\":\"00000000-0000-4000-8000-000000000001\",\"firstIndex\":1,"
            + "\"lastIndex\":2,\"createdAt\":\"2026-10-21T12:00:00Z\"}";

    // then
    assertThatThrownBy(() -> BackupLog.fromJson(json.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(InvalidBackupLogException.class)
        .hasMessageStartingWith("Expected the document to be a valid backup log, but got {");
  }

  @Test
  void shouldNameItByItsZeroPaddedCheckpointIdUnderTheLog() {
    // given
    final var log = new LogId("prod", "default", 3);

    // when
    final var key = ObjectNames.backupLog(log, 17);

    // then
    assertThat(key).isEqualTo("prod/default/3/backups/00000000000000000017.json");
    assertThat(key).startsWith(ObjectNames.backupLogsPrefix(log));
  }

  @Test
  void shouldRejectANegativeCheckpointIdInItsName() {
    assertThatThrownBy(() -> ObjectNames.backupLog(new LogId("prod", "default", 3), -1))
        .isInstanceOf(InvalidObjectNameException.class);
  }
}
