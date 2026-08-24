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
import io.camunda.operate.OperatePropertiesOverride;
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
              // DB url
              "camunda.database.url=http://url-for-exporter:4321",
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
              "camunda.database.url=http://some-legacy-url:/1234");

  @Test
  void testBrokerShouldFailWhenUsingLegacyDatabaseProperties() {
    brokerRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(UnifiedConfigurationException.class)
              .rootCause()
              .hasMessageContaining("Ambiguous configuration")
              .hasMessageContaining("conflicts");
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
              .hasMessageContaining("conflicts");
        });
  }
}
