/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class SecondaryStorageFailureWithLegacySchemaTest {

  private final ApplicationContextRunner operateRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class,
              OperatePropertiesOverride.class,
              SearchEngineConnectPropertiesOverride.class)
          .withPropertyValues(
              "camunda.database.type=elasticsearch",
              "camunda.operate.database=elasticsearch",
              "camunda.tasklist.database=elasticsearch",
              "camunda.database.url=http://some-legacy-url:/1234");

  private final ApplicationContextRunner tasklistRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class,
              TasklistPropertiesOverride.class,
              SearchEngineConnectPropertiesOverride.class)
          .withPropertyValues(
              "camunda.database.type=elasticsearch",
              "camunda.operate.database=elasticsearch",
              "camunda.tasklist.database=elasticsearch",
              "camunda.database.url=http://some-legacy-url:/1234");

  private final ApplicationContextRunner tasklistRunnerWithMismatchingConfigs =
      new ApplicationContextRunner()
          .withUserConfiguration(
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class,
              TasklistPropertiesOverride.class,
              SearchEngineConnectPropertiesOverride.class)
          .withPropertyValues(
              // type
              "camunda.data.secondary-storage.type=elasticsearch",
              "camunda.database.type=elasticsearch",
              "camunda.operate.database=elasticsearch",
              "camunda.tasklist.database=elasticsearch",
              // url
              "camunda.data.secondary-storage.elasticsearch.url=http://new-mismatching-url:4321",
              "camunda.database.url=http://legacy-mismatching-url:4321",
              "camunda.tasklist.elasticsearch.url=http://legacy-mismatching-url:4321",
              "camunda.operate.elasticsearch.url=http://legacy-mismatching-url:4321");

  @Test
  void testOperateshouldFailWhenUsingLegacyDatabaseProperties() {
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

  @Test
  void testTasklistshouldFailWhenUsingLegacyDatabaseProperties() {
    tasklistRunner.run(
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
  void testTasklistshouldFailWhenUsingLegacyDatabasePropertiesDontMatchNewProperties() {
    tasklistRunnerWithMismatchingConfigs.run(
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
