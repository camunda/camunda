/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class PhysicalTenantSearchClientResourcesTest {

  private static final String UNREACHABLE = "http://localhost:1";

  @Test
  void shouldNotOpenATenantsClientBeforeItsFirstRequest() {
    // given
    try (final var resources = resources(Duration.ZERO, "elasticsearch", "opensearch")) {

      // when
      final var clients = resources.searchClients();

      // then
      assertThat(clients.esClients()).containsKey("elasticsearch");
      assertThat(clients.osClients()).containsKey("opensearch");
      assertThat(clients.osAsyncClients()).containsKey("opensearch");
      assertThat(resources.isCreated("elasticsearch")).isFalse();
      assertThat(resources.isCreated("opensearch")).isFalse();
    }
  }

  @Test
  void shouldOpenATenantsClientOnItsFirstRequest() {
    // given
    try (final var resources = resources(Duration.ZERO, "elasticsearch", "opensearch")) {
      final var es = resources.searchClients().esClients().get("elasticsearch");

      // when
      assertThatThrownBy(es::info).isNotNull();

      // then
      assertThat(resources.isCreated("elasticsearch")).isTrue();
      assertThat(resources.isCreated("opensearch")).isFalse();
    }
  }

  @Test
  void shouldReleaseATenantsClientThatGoesUnusedAndOpenItAgainOnTheNextRequest() {
    // given
    try (final var resources = resources(Duration.ofMillis(200), "opensearch")) {
      final var os = resources.searchClients().osClients().get("opensearch");
      assertThatThrownBy(os::info).isNotNull();

      // when
      Awaitility.await("the idle client is released")
          .atMost(Duration.ofSeconds(10))
          .until(() -> !resources.isCreated("opensearch"));

      // then
      assertThatThrownBy(os::info).isNotNull();
      assertThat(resources.isCreated("opensearch")).isTrue();
    }
  }

  @Test
  void shouldNotOpenATenantsClientForBackgroundWork() {
    // given
    try (final var resources = resources(Duration.ZERO, "elasticsearch")) {

      // when
      final var result = resources.whileCreated("elasticsearch", () -> "checked");

      // then
      assertThat(result).isEmpty();
      assertThat(resources.isCreated("elasticsearch")).isFalse();
    }
  }

  private static PhysicalTenantSearchClientResources resources(
      final Duration idleReleaseTimeout, final String... types) {
    final var configs = new LinkedHashMap<String, ConnectConfiguration>();
    for (final var type : types) {
      final var config = new ConnectConfiguration();
      config.setType(type);
      config.setUrl(UNREACHABLE);
      configs.put(type, config);
    }
    return PhysicalTenantSearchClientResources.create(
        Map.copyOf(configs), tenantId -> idleReleaseTimeout);
  }
}
