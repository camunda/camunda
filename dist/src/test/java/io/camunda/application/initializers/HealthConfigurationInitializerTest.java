/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.application.Profile;
import io.camunda.application.commons.search.SchemaReadinessCheck;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class HealthConfigurationInitializerTest {

  private HealthConfigurationInitializer initializer;
  private Environment environment;

  @BeforeEach
  void setUp() {
    environment = mock(Environment.class);
    initializer = new HealthConfigurationInitializer();
  }

  /** Helper to enable ES secondary storage (the default when the property is not set). */
  private void withElasticsearchSecondaryStorage() {
    when(environment.getProperty("camunda.data.secondary-storage.type"))
        .thenReturn("elasticsearch");
  }

  /** Helper to enable RDBMS as secondary storage (disables ES-specific checks). */
  private void withRdbmsSecondaryStorage() {
    when(environment.getProperty("camunda.data.secondary-storage.type")).thenReturn("rdbms");
  }

  /** Helper to disable secondary storage entirely. */
  private void withNoSecondaryStorage() {
    when(environment.getProperty("camunda.data.secondary-storage.type")).thenReturn("none");
  }

  /** Helper to set the embedded gateway property. */
  private void withEmbeddedGatewayEnabled(final boolean enabled) {
    when(environment.getProperty("zeebe.broker.gateway.enable", Boolean.class, true))
        .thenReturn(enabled);
  }

  @Nested
  class BrokerProfile {

    @Test
    void shouldIncludeBrokerHealthIndicators() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.BROKER.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).containsExactly("brokerReady", "nodeIdProviderReady");
    }

    @Test
    void shouldIncludeSchemaReadinessCheckWithEmbeddedGatewayAndES() {
      // given — broker with embedded gateway (default) and ES
      withElasticsearchSecondaryStorage();
      withEmbeddedGatewayEnabled(true);
      final var profiles = List.of(Profile.BROKER.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains(SchemaReadinessCheck.SCHEMA_READINESS_CHECK);
    }

    @Test
    void shouldNotIncludeSchemaReadinessCheckWithoutEmbeddedGateway() {
      // given — broker without embedded gateway does not serve traffic directly
      withElasticsearchSecondaryStorage();
      withEmbeddedGatewayEnabled(false);
      final var profiles = List.of(Profile.BROKER.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).doesNotContain(SchemaReadinessCheck.SCHEMA_READINESS_CHECK);
    }
  }

  @Nested
  class GatewayProfile {

    @Test
    void shouldIncludeGatewayStartedIndicator() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).containsExactly("gatewayStarted");
    }

    @Test
    void shouldIncludeSchemaReadinessCheckWithES() {
      // given — standalone gateway with ES
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains(SchemaReadinessCheck.SCHEMA_READINESS_CHECK);
    }
  }

  @Nested
  class BrokerAndGatewayProfiles {

    @Test
    void shouldIncludeBothBrokerAndGatewayIndicators() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.BROKER.getId(), Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .containsExactly("brokerReady", "nodeIdProviderReady", "gatewayStarted");
    }
  }

  @Nested
  class SecondaryStorageDisabled {

    @Test
    void shouldNotIncludeSearchIndicatorsWhenSecondaryStorageIsNone() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.OPERATE.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).isEmpty();
    }
  }

  @Nested
  class ElasticsearchSecondaryStorage {

    @Test
    void shouldIncludeReadinessStateAndSchemaCheckForOperate() {
      // given
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.OPERATE.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then — Operate is a webapp profile, so it serves traffic
      assertThat(indicators)
          .contains("readinessState")
          .contains(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
          .contains("indicesCheck");
    }

    @Test
    void shouldIncludeReadinessStateAndSchemaCheckForTasklist() {
      // given
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("readinessState")
          .contains(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
          .contains("searchEngineCheck");
    }

    @Test
    void shouldIncludeReadinessStateAndSchemaCheckForIdentity() {
      // given
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.IDENTITY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("readinessState")
          .contains(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
          .doesNotContain("indicesCheck")
          .doesNotContain("searchEngineCheck");
    }

    @Test
    void shouldIncludeReadinessStateAndSchemaCheckForAdmin() {
      // given
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.ADMIN.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("readinessState")
          .contains(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
          .doesNotContain("indicesCheck")
          .doesNotContain("searchEngineCheck");
    }

    @Test
    void shouldIncludeOperateAndTasklistIndicatorsWhenBothActive() {
      // given
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.OPERATE.getId(), Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("readinessState")
          .contains(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
          .contains("indicesCheck")
          .contains("searchEngineCheck");
    }
  }

  @Nested
  class RdbmsSecondaryStorage {

    @Test
    void shouldIncludeReadinessStateButNotSearchIndicatorsForOperate() {
      // given
      withRdbmsSecondaryStorage();
      final var profiles = List.of(Profile.OPERATE.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("readinessState")
          .doesNotContain("indicesCheck")
          .doesNotContain("searchEngineCheck")
          .doesNotContain(SchemaReadinessCheck.SCHEMA_READINESS_CHECK);
    }

    @Test
    void shouldIncludeReadinessStateButNotSearchIndicatorsForTasklist() {
      // given
      withRdbmsSecondaryStorage();
      final var profiles = List.of(Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("readinessState")
          .doesNotContain("searchEngineCheck")
          .doesNotContain(SchemaReadinessCheck.SCHEMA_READINESS_CHECK);
    }
  }

  @Nested
  class RestoreProfile {

    @Test
    void shouldNotIncludeSchemaReadinessCheckForRestore() {
      // given — RESTORE does not serve traffic, so schema readiness is irrelevant
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.RESTORE.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .doesNotContain(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
          .doesNotContain("readinessState");
    }
  }

  @Nested
  class FullDeploymentProfile {

    @Test
    void shouldIncludeAllIndicatorsForFullDeploymentWithES() {
      // given — typical Camunda deployment: broker + gateway + operate + tasklist
      withElasticsearchSecondaryStorage();
      withEmbeddedGatewayEnabled(true);
      final var profiles =
          List.of(
              Profile.BROKER.getId(),
              Profile.GATEWAY.getId(),
              Profile.OPERATE.getId(),
              Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains(
              "brokerReady",
              "nodeIdProviderReady",
              "gatewayStarted",
              "readinessState",
              SchemaReadinessCheck.SCHEMA_READINESS_CHECK,
              "indicesCheck",
              "searchEngineCheck");
    }

    @Test
    void shouldNotDuplicateIndicatorsWhenMultipleProfilesServeTraffic() {
      // given — broker + gateway + all webapps: schemaReadinessCheck should appear exactly once
      withElasticsearchSecondaryStorage();
      withEmbeddedGatewayEnabled(true);
      final var profiles =
          List.of(
              Profile.BROKER.getId(),
              Profile.GATEWAY.getId(),
              Profile.OPERATE.getId(),
              Profile.TASKLIST.getId(),
              Profile.IDENTITY.getId(),
              Profile.ADMIN.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then — each indicator should appear exactly once
      assertThat(indicators.stream().filter("readinessState"::equals)).hasSize(1);
      assertThat(indicators.stream().filter(SchemaReadinessCheck.SCHEMA_READINESS_CHECK::equals))
          .hasSize(1);
    }
  }

  @Nested
  class NoProfiles {

    @Test
    void shouldReturnEmptyListWhenNoProfilesActive() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.<String>of();

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).isEmpty();
    }
  }

  @Nested
  class LivenessGroup {

    @Test
    void shouldIncludeBrokerIndicatorsForBrokerProfile() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.BROKER.getId());

      // when
      final var indicators =
          initializer.collectLivenessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).containsExactly("brokerReady", "nodeIdProviderReady");
    }

    @Test
    void shouldIncludeGatewayLivenessIndicatorsForGatewayProfile() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectLivenessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .containsExactly(
              "livenessGatewayClusterAwareness",
              "livenessGatewayPartitionLeaderAwareness",
              "livenessMemory");
    }

    @Test
    void shouldIncludeBothBrokerAndGatewayLivenessIndicators() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.BROKER.getId(), Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectLivenessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .containsExactly(
              "brokerReady",
              "nodeIdProviderReady",
              "livenessGatewayClusterAwareness",
              "livenessGatewayPartitionLeaderAwareness",
              "livenessMemory");
    }

    @Test
    void shouldReturnEmptyForWebappOnlyProfiles() {
      // given — webapp profiles have no liveness-specific indicators
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.OPERATE.getId());

      // when
      final var indicators =
          initializer.collectLivenessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).isEmpty();
    }

    @Test
    void shouldNotIncludeEsDependentOrReadinessIndicators() {
      // given — ES is configured, but liveness must remain ES-independent
      withElasticsearchSecondaryStorage();
      withEmbeddedGatewayEnabled(true);
      final var profiles =
          List.of(
              Profile.BROKER.getId(),
              Profile.OPERATE.getId(),
              Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectLivenessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .doesNotContain(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
          .doesNotContain("indicesCheck")
          .doesNotContain("searchEngineCheck")
          .doesNotContain("readinessState");
    }

    @Test
    void shouldReturnEmptyListWhenNoProfilesActive() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.<String>of();

      // when
      final var indicators =
          initializer.collectLivenessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).isEmpty();
    }

    @Test
    void shouldIncludeAllIndicatorsForFullDeployment() {
      // given — typical all-in-one + gateway deployment
      withElasticsearchSecondaryStorage();
      withEmbeddedGatewayEnabled(true);
      final var profiles =
          List.of(
              Profile.BROKER.getId(),
              Profile.GATEWAY.getId(),
              Profile.OPERATE.getId(),
              Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectLivenessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .containsExactly(
              "brokerReady",
              "nodeIdProviderReady",
              "livenessGatewayClusterAwareness",
              "livenessGatewayPartitionLeaderAwareness",
              "livenessMemory")
          .doesNotContain(SchemaReadinessCheck.SCHEMA_READINESS_CHECK)
          .doesNotContain("indicesCheck")
          .doesNotContain("searchEngineCheck")
          .doesNotContain("readinessState");
    }
  }
}
