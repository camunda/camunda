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
    @ValueSource(strings = {"operate", "tasklist", "admin"})
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
    @ValueSource(strings = {"operate", "tasklist", "admin"})
    void shouldIncludeReadinessStateForAnyWebappProfile(final String profile) {
      // given — Admin is a webapp profile, should get readinessState
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(profile);

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("readinessState");
    }

    @Test
    void shouldNotDuplicateReadinessStateForMultipleWebappProfiles() {
      // given
      withElasticsearchSecondaryStorage();
      final var profiles = Profile.getWebappProfiles().stream().map(Profile::getId).toList();

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then — readinessState should appear exactly once
      assertThat(indicators.stream().filter("readinessState"::equals)).hasSize(1);
    }

    @Test
    void shouldIncludeIndicesCheckForOperateWithES() {
      // given
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.OPERATE.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("readinessState", "indicesCheck");
    }

    @Test
    void shouldIncludeSearchEngineCheckForTasklistWithES() {
      // given
      withElasticsearchSecondaryStorage();
      final var profiles = List.of(Profile.TASKLIST.getId());

      // when
      final var indicators =
          initializer.collectReadinessGroupHealthIndicators(profiles, environment);

      // then
      assertThat(indicators).contains("readinessState", "searchEngineCheck");
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
          .doesNotContain("indicesCheck", "searchEngineCheck");
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
  }
}
