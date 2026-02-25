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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;

class HealthConfigurationInitializerTest {

  private HealthConfigurationInitializer initializer;
  private Environment environment;

  @BeforeEach
  void setUp() {
    initializer = new HealthConfigurationInitializer();
    environment = mock(Environment.class);
  }

  private void withElasticsearchSecondaryStorage() {
    when(environment.getProperty("camunda.data.secondary-storage.type"))
        .thenReturn("elasticsearch");
  }

  private void withRdbmsSecondaryStorage() {
    when(environment.getProperty("camunda.data.secondary-storage.type")).thenReturn("rdbms");
  }

  private void withNoSecondaryStorage() {
    when(environment.getProperty("camunda.data.secondary-storage.type")).thenReturn("none");
  }

  private void withEmbeddedGateway(final boolean enabled) {
    when(environment.getProperty("zeebe.broker.gateway.enable", Boolean.class, true))
        .thenReturn(enabled);
  }

  private void withHttpGatewayEnabled() {
    withEmbeddedGateway(true);
    when(environment.getProperty("camunda.rest.enabled", Boolean.class, true)).thenReturn(true);
    when(environment.getProperty("camunda.mcp.enabled", Boolean.class, false)).thenReturn(false);
  }

  @Nested
  class LivenessGroup {

    @Test
    void shouldReturnBrokerIndicatorsForBrokerProfile() {
      // given
      final var profiles = List.of(Profile.BROKER.getId());

      // when
      final var indicators = initializer.collectLivenessGroupHealthIndicators(profiles);

      // then
      assertThat(indicators).containsExactly("brokerReady", "nodeIdProviderReady");
    }

    @Test
    void shouldReturnGatewayLivenessIndicatorsForGatewayProfile() {
      // given
      final var profiles = List.of(Profile.GATEWAY.getId());

      // when
      final var indicators = initializer.collectLivenessGroupHealthIndicators(profiles);

      // then
      assertThat(indicators)
          .containsExactly(
              "livenessGatewayClusterAwareness",
              "livenessGatewayPartitionLeaderAwareness",
              "livenessMemory");
    }

    @Test
    void shouldReturnBrokerIndicatorsForAllInOneMode() {
      // given — all-in-one mode: broker + webapps, no GATEWAY profile
      final var profiles =
          List.of(Profile.BROKER.getId(), Profile.OPERATE.getId(), Profile.TASKLIST.getId());

      // when
      final var indicators = initializer.collectLivenessGroupHealthIndicators(profiles);

      // then — only broker indicators, no ES-dependent indicators from webapp profiles
      assertThat(indicators)
          .containsExactly("brokerReady", "nodeIdProviderReady")
          .doesNotContain("indicesCheck", "searchEngineCheck");
    }

    @ParameterizedTest
    @ValueSource(strings = {"operate", "tasklist", "identity", "admin"})
    void shouldReturnEmptyForWebappOnlyProfiles(final String profile) {
      // given
      final var profiles = List.of(profile);

      // when
      final var indicators = initializer.collectLivenessGroupHealthIndicators(profiles);

      // then
      assertThat(indicators).isEmpty();
    }

    @Test
    void shouldNeverContainEsDependentIndicators() {
      // given — full deployment with all profiles
      final var profiles = Stream.of(Profile.values()).map(Profile::getId).toList();

      // when
      final var indicators = initializer.collectLivenessGroupHealthIndicators(profiles);

      // then — must not contain any ES-dependent indicators
      assertThat(indicators).doesNotContain("indicesCheck", "searchEngineCheck");
    }
  }

  @Nested
  class ReadinessGroup {

    @Test
    void shouldIncludeBrokerIndicators() {
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

    @ParameterizedTest
    @ValueSource(strings = {"operate", "tasklist", "identity", "admin"})
    void shouldIncludeReadinessStateForAnyWebappProfile(final String profile) {
      // given — Identity is a webapp profile, should get readinessState
      withElasticsearchSecondaryStorage();
      withHttpGatewayEnabled();
      final var profiles = List.of(profile);

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("readinessState", "schemaReadinessCheck");
    }

    @Test
    void shouldNotDuplicateReadinessStateForMultipleWebappProfiles() {
      // given
      withElasticsearchSecondaryStorage();
      withHttpGatewayEnabled();
      final var profiles = Profile.getWebappProfiles().stream().map(Profile::getId).toList();

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then — readinessState should appear exactly once
      assertThat(indicators.stream().filter("readinessState"::equals)).hasSize(1);
      // then — schemaReadinessCheck should appear exactly once
      assertThat(indicators.stream().filter("schemaReadinessCheck"::equals)).hasSize(1);
    }

    @Test
    void shouldIncludeIndicesCheckForOperateWithES() {
      // given
      withElasticsearchSecondaryStorage();
      withHttpGatewayEnabled();
      final var profiles = List.of(Profile.OPERATE.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("readinessState", "indicesCheck", "schemaReadinessCheck");
    }

    @Test
    void shouldIncludeSearchEngineCheckForTasklistWithES() {
      // given
      withElasticsearchSecondaryStorage();
      withHttpGatewayEnabled();
      final var profiles = List.of(Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("readinessState", "searchEngineCheck", "schemaReadinessCheck");
    }

    @Test
    void shouldNotIncludeEsIndicatorsWithRdbms() {
      // given
      withRdbmsSecondaryStorage();
      final var profiles = List.of(Profile.OPERATE.getId(), Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("readinessState")
          .doesNotContain("indicesCheck", "searchEngineCheck", "schemaReadinessCheck");
    }

    @Test
    void shouldNotIncludeSecondaryStorageIndicatorsWhenDisabled() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.OPERATE.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).isEmpty();
    }

    @Test
    void shouldIncludeSchemaReadinessCheckForGatewayWithES() {
      // given
      withElasticsearchSecondaryStorage();
      withHttpGatewayEnabled();
      final var profiles = List.of(Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("gatewayStarted", "schemaReadinessCheck");
    }

    @Test
    void shouldIncludeSchemaReadinessCheckForBrokerWithEmbeddedGatewayEnabled() {
      // given
      withElasticsearchSecondaryStorage();
      withHttpGatewayEnabled();
      final var profiles = List.of(Profile.BROKER.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("brokerReady", "nodeIdProviderReady", "schemaReadinessCheck");
    }

    @Test
    void shouldNotIncludeSchemaReadinessCheckForBrokerWithEmbeddedGatewayDisabled() {
      // given
      withElasticsearchSecondaryStorage();
      withEmbeddedGateway(false);
      final var profiles = List.of(Profile.BROKER.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators)
          .contains("brokerReady", "nodeIdProviderReady")
          .doesNotContain("schemaReadinessCheck");
    }

    @Test
    void shouldNotIncludeSchemaReadinessCheckWithNoSecondaryStorage() {
      // given
      withNoSecondaryStorage();
      final var profiles = List.of(Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("gatewayStarted").doesNotContain("schemaReadinessCheck");
    }

    @Test
    void shouldNotIncludeSchemaReadinessCheckWithRdbms() {
      // given
      withRdbmsSecondaryStorage();
      final var profiles = List.of(Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("gatewayStarted").doesNotContain("schemaReadinessCheck");
    }

    @Test
    void shouldIncludeSchemaReadinessCheckForBrokerWithGatewayProfileAndES() {
      // given — broker + gateway profiles with ES secondary storage and gateway enabled
      withElasticsearchSecondaryStorage();
      withHttpGatewayEnabled();
      final var profiles = List.of(Profile.BROKER.getId(), Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then — schemaReadinessCheck should be included because gateway is enabled
      assertThat(indicators)
          .contains("brokerReady", "nodeIdProviderReady", "gatewayStarted", "schemaReadinessCheck");
    }

    @Test
    void shouldIncludeSchemaReadinessCheckForBrokerWithGatewayProfileAndEmbeddedGatewayDisabled() {
      // given — broker + gateway profiles, embedded gateway disabled but gateway profile active
      withElasticsearchSecondaryStorage();
      withEmbeddedGateway(false);
      when(environment.getProperty("camunda.rest.enabled", Boolean.class, true)).thenReturn(true);
      when(environment.getProperty("camunda.mcp.enabled", Boolean.class, false)).thenReturn(false);
      final var profiles = List.of(Profile.BROKER.getId(), Profile.GATEWAY.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then — schemaReadinessCheck NOT included because isAnyHttpGatewayEnabled is false
      assertThat(indicators)
          .contains("brokerReady", "nodeIdProviderReady", "gatewayStarted")
          .doesNotContain("schemaReadinessCheck");
    }

    @Test
    void shouldNotIncludeSchemaReadinessCheckWithoutRelevantProfile() {
      // given — ES storage and gateway enabled, but no broker/gateway/webapp profile
      withElasticsearchSecondaryStorage();
      withHttpGatewayEnabled();
      final var profiles = List.of(Profile.RESTORE.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then — no schemaReadinessCheck because no relevant profile is active
      assertThat(indicators).doesNotContain("schemaReadinessCheck");
    }
  }
}
