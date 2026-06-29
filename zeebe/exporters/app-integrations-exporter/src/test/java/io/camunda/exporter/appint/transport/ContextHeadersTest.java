/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextHeadersTest {

  @Test
  void shouldResolveAllHeadersWhenEverythingAvailable() {
    // given / when
    final var headers = ContextHeaders.resolve(null, "cluster-env", "org-123");

    // then
    assertThat(collect(headers))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                ContextHeaders.X_ORG_ID, "org-123",
                ContextHeaders.X_CLUSTER_ID, "cluster-env"));
  }

  @Test
  void shouldPreferConfiguredClusterIdOverEnvClusterId() {
    // given / when
    final var headers = ContextHeaders.resolve("cluster-config", "cluster-env", null);

    // then
    assertThat(collect(headers)).containsEntry(ContextHeaders.X_CLUSTER_ID, "cluster-config");
  }

  @Test
  void shouldFallBackToEnvClusterIdWhenConfiguredIsBlank() {
    // given / when
    final var headers = ContextHeaders.resolve("  ", "cluster-env", null);

    // then
    assertThat(collect(headers)).containsEntry(ContextHeaders.X_CLUSTER_ID, "cluster-env");
  }

  @Test
  void shouldOmitOrgIdWhenBlank() {
    // given / when
    final var headers = ContextHeaders.resolve("cluster", null, "   ");

    // then
    assertThat(collect(headers)).doesNotContainKey(ContextHeaders.X_ORG_ID);
  }

  @Test
  void shouldOmitOrgIdWhenNullSentinel() {
    // given / when
    final var headers = ContextHeaders.resolve("cluster", null, "null");

    // then
    assertThat(collect(headers)).doesNotContainKey(ContextHeaders.X_ORG_ID);
  }

  @Test
  void shouldEmitNoHeadersWhenNothingAvailable() {
    // given / when
    final var headers = ContextHeaders.resolve("  ", "", null);

    // then
    assertThat(collect(headers)).isEmpty();
  }

  @Test
  void emptyShouldEmitNoHeaders() {
    // given / when / then
    assertThat(collect(ContextHeaders.EMPTY)).isEmpty();
  }

  private static Map<String, String> collect(final ContextHeaders headers) {
    final Map<String, String> collected = new LinkedHashMap<>();
    headers.applyTo(collected::put);
    return collected;
  }
}
