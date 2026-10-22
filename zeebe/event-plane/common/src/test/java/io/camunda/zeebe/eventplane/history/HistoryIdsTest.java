/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HistoryIdsTest {

  @Test
  void shouldMakeTheNameBasedUuidOfTheClusterAndTenant() {
    // when
    final var id = HistoryIds.initial("my-cluster", "default");

    // then the MD5-based UUID of "event-plane/initial/my-cluster/default", worked out apart
    assertThat(id).isEqualTo(UUID.fromString("990a6ed2-8d83-31ac-845e-932cfa594b1b"));
    assertThat(id.version()).isEqualTo(3);
  }

  @Test
  void shouldMakeTheSameIdEveryTime() {
    // when, then every partition agrees on it without talking
    assertThat(HistoryIds.initial("my-cluster", "default"))
        .isEqualTo(HistoryIds.initial("my-cluster", "default"));
  }

  @Test
  void shouldMakeAnotherIdForAnotherClusterOrTenant() {
    // when, then
    assertThat(HistoryIds.initial("other-cluster", "default"))
        .isEqualTo(UUID.fromString("a74a0a62-48d7-3b9d-929d-fc66d6d8c671"))
        .isNotEqualTo(HistoryIds.initial("my-cluster", "default"));
    assertThat(HistoryIds.initial("my-cluster", "other"))
        .isEqualTo(UUID.fromString("a1b81efb-d35e-36c8-b61f-419069a75db5"))
        .isNotEqualTo(HistoryIds.initial("my-cluster", "default"));
  }

  @Test
  void shouldNameTheClusterAndTenantAsUtf8() {
    // when, then
    assertThat(HistoryIds.initial("größe", "ü")).isEqualTo(HistoryIds.initial("größe", "ü"));
    assertThat(HistoryIds.initial("größe", "ü")).isNotEqualTo(HistoryIds.initial("groesse", "ue"));
  }
}
