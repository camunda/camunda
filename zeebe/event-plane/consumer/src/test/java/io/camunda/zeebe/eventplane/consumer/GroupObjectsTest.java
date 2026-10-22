/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The JSON of the group objects and retention markers, as the contracts show it. */
final class GroupObjectsTest {

  private static final UUID HISTORY = UUID.fromString("0f6c5a52-3c1e-4f0e-9a51-0d1b2c3d4e5f");

  @Test
  void shouldWriteAHeartbeatAsTheContractShowsIt() throws Exception {
    // given
    final var heartbeat = new Heartbeat("w-1", 42);

    // when
    final var json = text(heartbeat.toJson());

    // then
    assertThat(json).isEqualTo("{\"version\":1,\"worker\":\"w-1\",\"beat\":42}");
    assertThat(Heartbeat.fromJson(heartbeat.toJson())).isEqualTo(heartbeat);
  }

  @Test
  void shouldWriteACursorAsTheContractShowsIt() throws Exception {
    // given
    final var cursor = new GroupCursor(new Cursor(HISTORY, 1096704, 1052032), "w-1");

    // when
    final var json = text(cursor.toJson());

    // then
    assertThat(json)
        .isEqualTo(
            "{\"version\":1,\"history\":\"%s\",\"index\":1096704,\"position\":1052032,\"worker\":\"w-1\"}"
                .formatted(HISTORY));
    assertThat(GroupCursor.fromJson(cursor.toJson())).isEqualTo(cursor);
  }

  @Test
  void shouldWriteAGroupAsTheContractShowsIt() throws Exception {
    // given
    final var group = new GroupDefinition("g", List.of(new Subscription("c", "*")), Start.EARLIEST);

    // when
    final var json = text(group.toJson());

    // then
    assertThat(json)
        .isEqualTo(
            "{\"version\":1,\"name\":\"g\",\"subscription\":[{\"cluster\":\"c\",\"tenant\":\"*\"}],\"start\":\"earliest\"}");
    assertThat(GroupDefinition.fromJson(group.toJson())).isEqualTo(group);
  }

  @Test
  void shouldWriteARetentionMarkerAsTheContractShowsIt() throws Exception {
    // given
    final var marker =
        new RetentionMarker(HISTORY, 1096704, 1052032, Instant.parse("2026-10-20T12:00:00Z"));

    // when
    final var json = text(marker.toJson());

    // then
    assertThat(json)
        .isEqualTo(
            "{\"version\":1,\"history\":\"%s\",\"firstIndex\":1096704,\"firstPosition\":1052032,\"createdAt\":\"2026-10-20T12:00:00Z\"}"
                .formatted(HISTORY));
    assertThat(RetentionMarker.fromJson(marker.toJson())).isEqualTo(marker);
  }

  @Test
  void shouldSkipFieldsItDoesNotKnow() throws Exception {
    // given
    final var json = "{\"version\":1,\"worker\":\"w-1\",\"beat\":7,\"host\":{\"name\":\"a\"}}";

    // when
    final var heartbeat = Heartbeat.fromJson(json.getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(heartbeat).isEqualTo(new Heartbeat("w-1", 7));
  }

  @Test
  void shouldNameTheFieldThatIsWrong() {
    // given
    final var json =
        "{\"version\":1,\"history\":\"%s\",\"index\":\"one\",\"position\":1,\"worker\":\"w\"}"
            .formatted(HISTORY);

    // when / then
    assertThatThrownBy(() -> GroupCursor.fromJson(json.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOfSatisfying(
            InvalidObjectJsonException.class, e -> assertThat(e.field()).isEqualTo("index"))
        .hasMessage("Expected index to be a 64-bit integer, but got \"one\"");
  }

  @Test
  void shouldRejectAnotherVersion() {
    // given
    final var json = "{\"version\":2,\"worker\":\"w-1\",\"beat\":7}";

    // when / then
    assertThatThrownBy(() -> Heartbeat.fromJson(json.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(InvalidObjectJsonException.class)
        .hasMessage("Expected version to be 1, but got 2");
  }

  private static String text(final byte[] json) {
    return new String(json, StandardCharsets.UTF_8);
  }
}
