/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RestoredHistoryTest {

  private static final UUID HISTORY = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final UUID SOURCE = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final Instant NOON = Instant.parse("2026-10-20T12:00:00Z");

  @TempDir private Path directory;

  @Test
  void shouldWriteTheFilesJsonInTheContractsFieldOrder() {
    // given
    final var restored = new RestoredHistory(HISTORY, NOON, SOURCE, 1_096_704, 1_052_032);

    // when
    final var json = new String(restored.toJson(), StandardCharsets.UTF_8);

    // then
    assertThat(json)
        .isEqualTo(
            "{\"history\":\"00000000-0000-4000-8000-000000000002\","
                + "\"createdAt\":\"2026-10-20T12:00:00Z\","
                + "\"source\":\"00000000-0000-4000-8000-000000000001\","
                + "\"index\":1096704,\"position\":1052032}");
  }

  @Test
  void shouldWriteANullSourceForARestoreThatDoesNotKnowIt() {
    // given
    final var restored = new RestoredHistory(HISTORY, NOON, null, 7, 3);

    // when
    final var json = new String(restored.toJson(), StandardCharsets.UTF_8);

    // then
    assertThat(json).contains("\"source\":null,\"index\":7,\"position\":3");
  }

  @Test
  void shouldReadBackWhatItWrote() throws IOException {
    // given
    final var withSource =
        new RestoredHistory(HISTORY, Instant.parse("2026-10-20T12:00:00.123456Z"), SOURCE, 7, 3);
    final var withoutSource = new RestoredHistory(HISTORY, NOON, null, 7, 3);

    // when, then
    assertThat(RestoredHistory.fromJson(withSource.toJson())).isEqualTo(withSource);
    assertThat(RestoredHistory.fromJson(withoutSource.toJson())).isEqualTo(withoutSource);
  }

  @Test
  void shouldReadTheContractsExampleAndSkipFieldsItDoesNotKnow() throws IOException {
    // given
    final var json =
        """
        {"history":"00000000-0000-4000-8000-000000000002","createdAt":"2026-10-20T12:00:00Z",
         "source":"00000000-0000-4000-8000-000000000001","index":1096704,"position":1052032,
         "operator":"someone"}
        """;

    // when
    final var restored = RestoredHistory.fromJson(json.getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(restored)
        .isEqualTo(new RestoredHistory(HISTORY, NOON, SOURCE, 1_096_704, 1_052_032));
  }

  @Test
  void shouldGiveTheBaseTheHistoryTakesOver() {
    // given
    final var restored = new RestoredHistory(HISTORY, NOON, null, 1_096_704, 1_052_032);

    // when, then
    assertThat(restored.baseIn(SOURCE)).isEqualTo(new Base(SOURCE, 1_096_704, 1_052_032));
  }

  @Test
  void shouldRejectAHistoryThatIsNotOne() {
    // when, then
    assertThatThrownBy(() -> new RestoredHistory(HISTORY, NOON, HISTORY, 5, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected history %s to take over another history, but got itself".formatted(HISTORY));
    assertThatThrownBy(() -> new RestoredHistory(HISTORY, NOON, SOURCE, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RestoredHistory(HISTORY, NOON, null, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldNameTheFieldOfJsonThatIsWrong() {
    // given
    final var valid =
        """
        {"history":"00000000-0000-4000-8000-000000000002","createdAt":"2026-10-20T12:00:00Z",
         "source":"00000000-0000-4000-8000-000000000001","index":5,"position":3}
        """;

    // when, then
    assertThatThrownBy(() -> fromJson(valid.replace("\"history\":", "\"hist\":")))
        .isInstanceOfSatisfying(
            InvalidHistoryJsonException.class, e -> assertThat(e.field()).isEqualTo("history"))
        .hasMessage("Expected history to be a UUID, but got nothing");
    assertThatThrownBy(() -> fromJson(valid.replace("2026-10-20T12:00:00Z", "noon")))
        .isInstanceOfSatisfying(
            InvalidHistoryJsonException.class, e -> assertThat(e.field()).isEqualTo("createdAt"));
    assertThatThrownBy(() -> fromJson(valid.replace("\"index\":5", "\"index\":\"5\"")))
        .isInstanceOfSatisfying(
            InvalidHistoryJsonException.class, e -> assertThat(e.field()).isEqualTo("index"));
    assertThatThrownBy(() -> fromJson(valid.replace("\"position\":3", "\"position\":null")))
        .isInstanceOfSatisfying(
            InvalidHistoryJsonException.class, e -> assertThat(e.field()).isEqualTo("position"));
    assertThatThrownBy(
            () ->
                fromJson(
                    valid.replace("\"index\":5,\"position\":3", "\"index\":0,\"position\":-1")))
        .isInstanceOf(InvalidHistoryJsonException.class)
        .hasMessageContaining("Expected the document to be a valid restored history")
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> fromJson("{")).isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(() -> fromJson("[]")).isInstanceOf(InvalidHistoryJsonException.class);
  }

  @Test
  void shouldLeaveTheFileInAPartitionsDataDirectoryForThePublisher() throws IOException {
    // given
    final var restored = new RestoredHistory(HISTORY, NOON, SOURCE, 1_096_704, 1_052_032);

    // when
    restored.writeTo(directory);

    // then
    assertThat(directory.resolve("event-plane-history.json")).hasBinaryContent(restored.toJson());
    assertThat(RestoredHistory.FILE_NAME).isEqualTo("event-plane-history.json");
    assertThat(RestoredHistory.readFrom(directory)).contains(restored);
    try (final var files = Files.list(directory)) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .containsExactly("event-plane-history.json");
    }
  }

  @Test
  void shouldReplaceTheFileOfEarlierRestore() throws IOException {
    // given
    new RestoredHistory(SOURCE, NOON, null, 4, 2).writeTo(directory);
    final var later = new RestoredHistory(HISTORY, NOON.plusSeconds(60), SOURCE, 9, 8);

    // when
    later.writeTo(directory);

    // then
    assertThat(RestoredHistory.readFrom(directory)).contains(later);
  }

  @Test
  void shouldFindNoHistoryWhereNoRestoreLeftOne() throws IOException {
    // when, then
    assertThat(RestoredHistory.readFrom(directory)).isEmpty();
    assertThat(RestoredHistory.readFrom(directory.resolve("missing"))).isEmpty();
  }

  @Test
  void shouldFailToReadAFileThatIsNotAHistory() throws IOException {
    // given
    Files.writeString(directory.resolve(RestoredHistory.FILE_NAME), "{\"history\":1}");

    // when, then
    assertThatThrownBy(() -> RestoredHistory.readFrom(directory))
        .isInstanceOf(InvalidHistoryJsonException.class);
  }

  private static RestoredHistory fromJson(final String json) throws IOException {
    return RestoredHistory.fromJson(json.getBytes(StandardCharsets.UTF_8));
  }
}
