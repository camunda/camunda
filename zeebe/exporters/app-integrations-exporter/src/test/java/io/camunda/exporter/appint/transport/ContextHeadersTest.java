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
    final var headers = ContextHeaders.resolve(null, "cluster-ctx", "tenant-a", "org-123");

    // then
    assertThat(collect(headers))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                ContextHeaders.X_ORG_ID, "org-123",
                ContextHeaders.X_CLUSTER_ID, "cluster-ctx",
                ContextHeaders.X_PHYSICAL_TENANT_ID, "tenant-a"));
  }

  @Test
  void shouldPreferConfiguredClusterIdOverContextClusterId() {
    // given / when
    final var headers = ContextHeaders.resolve("cluster-config", "cluster-ctx", null, null);

    // then
    assertThat(collect(headers)).containsEntry(ContextHeaders.X_CLUSTER_ID, "cluster-config");
  }

  @Test
  void shouldFallBackToContextClusterIdWhenConfiguredIsBlank() {
    // given / when
    final var headers = ContextHeaders.resolve("  ", "cluster-ctx", null, null);

    // then
    assertThat(collect(headers)).containsEntry(ContextHeaders.X_CLUSTER_ID, "cluster-ctx");
  }

  @Test
  void shouldOmitOrgIdWhenBlank() {
    // given / when
    final var headers = ContextHeaders.resolve(null, "cluster", null, "   ");

    // then
    assertThat(collect(headers)).doesNotContainKey(ContextHeaders.X_ORG_ID);
  }

  @Test
  void shouldOmitOrgIdWhenNullSentinel() {
    // given / when
    final var headers = ContextHeaders.resolve(null, "cluster", null, "null");

    // then
    assertThat(collect(headers)).doesNotContainKey(ContextHeaders.X_ORG_ID);
  }

  @Test
  void shouldSendDefaultPhysicalTenant() {
    // given / when
    final var headers = ContextHeaders.resolve(null, null, "default", null);

    // then
    assertThat(collect(headers))
        .containsExactlyEntriesOf(Map.of(ContextHeaders.X_PHYSICAL_TENANT_ID, "default"));
  }

  @Test
  void shouldEmitNoHeadersWhenNothingAvailable() {
    // given / when
    final var headers = ContextHeaders.resolve(null, "", "  ", null);

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
