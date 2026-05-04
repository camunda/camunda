/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

class LegacyPropertyEnvVarTest {

  @Nested
  @SpringJUnitConfig(
      classes = {
        UnifiedConfiguration.class,
        BrokerBasedPropertiesOverride.class,
        UnifiedConfigurationHelper.class
      },
      initializers = WithAllUnderscores.EnvVarInitializer.class)
  @ActiveProfiles("broker")
  class WithAllUnderscores {

    @Autowired BrokerBasedProperties brokerCfg;

    @Test
    void shouldResolveKebabCaseEnvVar() {
      assertThat(brokerCfg.isExecutionMetricsExporterEnabled()).isTrue();
    }

    static class EnvVarInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
      @Override
      public void initialize(final ConfigurableApplicationContext ctx) {
        ctx.getEnvironment()
            .getPropertySources()
            .addFirst(
                new SystemEnvironmentPropertySource(
                    "testEnvVars",
                    Map.of("ZEEBE_BROKER_EXECUTION_METRICS_EXPORTER_ENABLED", "true")));
      }
    }
  }

  @Nested
  @SpringJUnitConfig(
      classes = {
        UnifiedConfiguration.class,
        BrokerBasedPropertiesOverride.class,
        UnifiedConfigurationHelper.class
      },
      initializers = WithStandardFormat.EnvVarInitializer.class)
  @ActiveProfiles("broker")
  class WithStandardFormat {

    @Autowired BrokerBasedProperties brokerCfg;

    @Test
    void shouldResolveCamelCaseEnvVar() {
      assertThat(brokerCfg.isExecutionMetricsExporterEnabled()).isTrue();
    }

    static class EnvVarInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
      @Override
      public void initialize(final ConfigurableApplicationContext ctx) {
        ctx.getEnvironment()
            .getPropertySources()
            .addFirst(
                new SystemEnvironmentPropertySource(
                    "testEnvVars", Map.of("ZEEBE_BROKER_EXECUTIONMETRICSEXPORTERENABLED", "true")));
      }
    }
  }

  @Nested
  @SpringJUnitConfig(
      classes = {
        UnifiedConfiguration.class,
        BrokerBasedPropertiesOverride.class,
        UnifiedConfigurationHelper.class
      },
      initializers = WithCollectionEnvVar.EnvVarInitializer.class)
  @ActiveProfiles("broker")
  class WithCollectionEnvVar {

    @Autowired BrokerBasedProperties brokerCfg;

    @Test
    void shouldResolveCollectionEnvVar() {
      assertThat(brokerCfg.getCluster().getInitialContactPoints())
          .isEqualTo(List.of("192.168.1.1:26502"));
    }

    // zeebe.broker.cluster.initialContactPoints is a List<String> legacy property whose camelCase
    // name maps to ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS in the Spring Boot-standard format.
    static class EnvVarInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
      @Override
      public void initialize(final ConfigurableApplicationContext ctx) {
        ctx.getEnvironment()
            .getPropertySources()
            .addFirst(
                new SystemEnvironmentPropertySource(
                    "testEnvVars",
                    Map.of("ZEEBE_BROKER_CLUSTER_INITIAL_CONTACT_POINTS", "192.168.1.1:26502")));
      }
    }
  }
}
