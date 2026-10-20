/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class UploadNoticeTest {

  private static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  private static final String KEY =
      "c/default/3/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/"
          + "00000000000000000001-00000000000000000040-L0-01JCZ4A1B2C3D4E5F6G7H8J9KM.seg";
  private static final String JSON =
      "{\"clusterId\":\"c\",\"tenantId\":\"default\",\"partition\":3,"
          + "\"history\":\"7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071\",\"key\":\""
          + KEY
          + "\",\"firstIndex\":1,\"lastIndex\":40,\"firstPosition\":1,\"lastPosition\":38,"
          + "\"size\":18432}";

  @Test
  void shouldWriteTheContractsJsonInItsFieldOrder() {
    // given
    final var notice = notice();

    // when
    final var json = new String(notice.toJson(), StandardCharsets.UTF_8);

    // then
    assertThat(json).isEqualTo(JSON);
  }

  @Test
  void shouldReadTheContractsJson() throws IOException {
    // when
    final var fromBytes = UploadNotice.fromJson(JSON.getBytes(StandardCharsets.UTF_8));
    final var fromText = UploadNotice.fromJson(JSON);

    // then
    assertThat(fromBytes).isEqualTo(notice()).isEqualTo(fromText);
  }

  @Test
  void shouldReadBackWhatItWrote() throws IOException {
    // given
    final var notice =
        new UploadNotice(
            "prod/eu \"c\"", "größe", 12, HISTORY, "a/b ü.seg", 0, Long.MAX_VALUE, -1, -1, 0);

    // when
    final var read = UploadNotice.fromJson(notice.toJson());

    // then
    assertThat(read).isEqualTo(notice);
  }

  @Test
  void shouldWriteTheSameBytesForTheSameNotice() {
    // when, then
    assertThat(notice().toJson()).isEqualTo(notice().toJson());
  }

  @Test
  void shouldSkipFieldsItDoesNotKnow() throws IOException {
    // given
    final var json = JSON.replace("\"size\":18432", "\"size\":18432,\"extra\":{\"a\":[1]}");

    // when, then
    assertThat(UploadNotice.fromJson(json)).isEqualTo(notice());
  }

  @Test
  void shouldRejectJsonWithAMissingField() {
    // when, then
    assertThatThrownBy(() -> UploadNotice.fromJson(JSON.replace("\"size\":18432", "\"x\":1")))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("size");
    assertThatThrownBy(() -> UploadNotice.fromJson(JSON.replace("\"key\":", "\"k\":")))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("key");
    assertThatThrownBy(() -> UploadNotice.fromJson(JSON.replace("\"partition\":3,", "")))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("partition");
  }

  @Test
  void shouldRejectJsonWithAFieldOfTheWrongType() {
    // when, then
    assertThatThrownBy(
            () -> UploadNotice.fromJson(JSON.replace("\"partition\":3", "\"partition\":\"x\"")))
        .isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(() -> UploadNotice.fromJson(JSON.replace("\"size\":18432", "\"size\":null")))
        .isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(
            () ->
                UploadNotice.fromJson(
                    JSON.replace("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071\",\"key", "nope\",\"key")))
        .isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(
            () -> UploadNotice.fromJson(JSON.replace("\"clusterId\":\"c\"", "\"clusterId\":null")))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void shouldRejectJsonThatIsNotAnObject() {
    // when, then
    assertThatThrownBy(() -> UploadNotice.fromJson("[]")).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> UploadNotice.fromJson("")).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> UploadNotice.fromJson("{")).isInstanceOf(IOException.class);
  }

  @Test
  void shouldRejectJsonOfANoticeThatBreaksARule() {
    // when, then
    assertThatThrownBy(
            () -> UploadNotice.fromJson(JSON.replace("\"lastIndex\":40", "\"lastIndex\":0")))
        .isInstanceOf(JsonProcessingException.class)
        .hasRootCauseMessage(
            "Expected a first index of 0 or more and a last index at or after it, but got 1 and 0");
  }

  @Test
  void shouldRejectValuesThatBreakARule() {
    // when, then
    assertThatThrownBy(() -> new UploadNotice(" ", "default", 3, HISTORY, KEY, 1, 40, 1, 38, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("to name its cluster, tenant and key");
    assertThatThrownBy(() -> new UploadNotice("c", "", 3, HISTORY, KEY, 1, 40, 1, 38, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new UploadNotice("c", "default", 3, HISTORY, "", 1, 40, 1, 38, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new UploadNotice("c", "default", 0, HISTORY, KEY, 1, 40, 1, 38, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected a partition of 1 or more, but got 0");
    assertThatThrownBy(() -> new UploadNotice("c", "default", 3, HISTORY, KEY, 41, 40, 1, 38, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected a first index of 0 or more and a last index at or after it, but got 41 and 40");
    assertThatThrownBy(() -> new UploadNotice("c", "default", 3, HISTORY, KEY, 1, 40, 39, 38, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected a last position at or after the first, but got 39 and 38");
    assertThatThrownBy(() -> new UploadNotice("c", "default", 3, HISTORY, KEY, 1, 40, 1, 38, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected a size of 0 or more, but got -1");
  }

  private static UploadNotice notice() {
    return new UploadNotice("c", "default", 3, HISTORY, KEY, 1, 40, 1, 38, 18432);
  }
}
