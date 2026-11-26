/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.secondarystorage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.operate.OperatePropertiesOverride;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class SecondaryStorageFailureWithLegacySchemaTest {

  private final ApplicationContextRunner brokerRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class,
              BrokerBasedPropertiesOverride.class)
          .withPropertyValues(
              "spring.profiles.active=broker",
              // DB type
              "camunda.database.type=opensearch",
              "camunda.operate.database=opensearch",
              "camunda.tasklist.database=opensearch",
              // DB url
              "camunda.database.url=http://url-for-exporter:4321",
              "camunda.tasklist.opensearch.url=http://url-for-exporter:4321",
              "camunda.operate.opensearch.url=http://url-for-exporter:4321");

  private final ApplicationContextRunner operateRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class,
              OperatePropertiesOverride.class)
          .withPropertyValues(
              "camunda.database.type=elasticsearch",
              "camunda.operate.database=elasticsearch",
              "camunda.tasklist.database=elasticsearch",
              "camunda.database.url=http://some-legacy-url:/1234");

  @AfterEach
  void clearPinnedEnvironment() {
    // The context runner pins its environment in UnifiedConfigurationHelper's static field;
    // clear it so the legacy properties set here do not leak into later tests in the same JVM.
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void testBrokerShouldFailWhenUsingLegacyDatabaseProperties() {
    brokerRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(UnifiedConfigurationException.class)
              .rootCause()
              .hasMessageContaining("Ambiguous configuration")
              .hasMessageContaining("conflicts")
              .hasMessageContaining("conflicts with the default")
              .hasMessageContaining("camunda.data.secondary-storage.type=opensearch");
        });
  }

  @Test
  void testSecurityEnabledOnlyInLegacyPropertyShouldFailAndExplainTheDefault() {
    new ApplicationContextRunner()
        .withUserConfiguration(
            UnifiedConfiguration.class,
            UnifiedConfigurationHelper.class,
            SearchEngineConnectPropertiesOverride.class)
        .withPropertyValues(
            "camunda.data.secondary-storage.type=elasticsearch",
            "camunda.database.security.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(UnifiedConfigurationException.class)
                  .rootCause()
                  .hasMessageContaining("conflicts with the default")
                  .hasMessageContaining(
                      "camunda.data.secondary-storage.elasticsearch.security.enabled=true");
            });
  }

  @Test
  void testOperateShouldFailWhenUsingLegacyDatabaseProperties() {
    operateRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(UnifiedConfigurationException.class)
              .rootCause()
              .hasMessageContaining("Ambiguous configuration")
              .hasMessageContaining("conflicts")
              .hasMessageContaining("conflicts with the default")
              .hasMessageContaining(
                  "camunda.data.secondary-storage.elasticsearch.url=http://some-legacy-url:/1234");
        });
  }
}
