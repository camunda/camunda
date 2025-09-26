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
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

public class SecondaryStorageFailureWithLegacySchemaTest {

  private static Supplier<ConfigurableApplicationContext> contextSupplier(
      final List<Class<?>> primarySources,
      final List<String> profiles,
      final Map<String, String> properties) {
    return () -> {
      final SpringApplication springApplication =
          new SpringApplication(primarySources.toArray(new Class<?>[0]));
      springApplication.setWebApplicationType(WebApplicationType.NONE);
      springApplication.setAdditionalProfiles(profiles.toArray(new String[0]));
      final MockEnvironment environment = new MockEnvironment();
      properties.forEach(environment::setProperty);
      springApplication.setEnvironment(environment);
      return springApplication.run();
    };
  }

  @Test
  void testBrokerShouldFailWhenUsingLegacyDatabaseProperties() {
    final AssertableApplicationContext context =
        AssertableApplicationContext.get(
            contextSupplier(
                List.of(UnifiedConfiguration.class, BrokerBasedPropertiesOverride.class),
                List.of("broker"),
                Map.ofEntries(
                    Map.entry("camunda.database.type", "opensearch"),
                    Map.entry("camunda.operate.database", "opensearch"),
                    Map.entry("camunda.tasklist.database", "opensearch"),
                    Map.entry("camunda.database.url", "http://url-for-exporter:4321"),
                    Map.entry("camunda.tasklist.opensearch.url", "http://url-for-exporter:4321"),
                    Map.entry("camunda.operate.opensearch.url", "http://url-for-exporter:4321"))));
    assertThat(context).hasFailed();
    assertThat(context.getStartupFailure())
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Ambiguous configuration")
        .hasMessageContaining("conflicting");
  }

  @Test
  void testOperateShouldFailWhenUsingLegacyDatabaseProperties() {
    final AssertableApplicationContext context =
        AssertableApplicationContext.get(
            contextSupplier(
                List.of(UnifiedConfiguration.class, OperatePropertiesOverride.class),
                List.of("operate"),
                Map.ofEntries(
                    Map.entry("camunda.database.type", "elasticsearch"),
                    Map.entry("camunda.operate.database", "elasticsearch"),
                    Map.entry("camunda.tasklist.database", "elasticsearch"),
                    Map.entry("camunda.database.url", "http://some-legacy-url:/1234"))));
    assertThat(context).hasFailed();
    assertThat(context.getStartupFailure())
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Ambiguous configuration")
        .hasMessageContaining("conflicting");
  }

  @Test
  void testTasklistShouldFailWhenUsingLegacyDatabaseProperties() {
    final AssertableApplicationContext context =
        AssertableApplicationContext.get(
            contextSupplier(
                List.of(UnifiedConfiguration.class, TasklistPropertiesOverride.class),
                List.of("tasklist"),
                Map.ofEntries(
                    Map.entry("camunda.database.type", "elasticsearch"),
                    Map.entry("camunda.operate.database", "elasticsearch"),
                    Map.entry("camunda.tasklist.database", "elasticsearch"),
                    Map.entry("camunda.database.url", "http://some-legacy-url:/1234"))));
    assertThat(context).hasFailed();
    assertThat(context.getStartupFailure())
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Ambiguous configuration")
        .hasMessageContaining("conflicting");
  }

  @Test
  void testTasklistShouldFailWhenUsingLegacyDatabasePropertiesDontMatchNewProperties() {
    final AssertableApplicationContext context =
        AssertableApplicationContext.get(
            contextSupplier(
                List.of(TasklistPropertiesOverride.class, UnifiedConfiguration.class),
                List.of("operate", "tasklist", "broker", "gateway"),
                Map.ofEntries(
                    // type
                    Map.entry("camunda.data.secondary-storage.type", "elasticsearch"),
                    Map.entry("camunda.database.type", "elasticsearch"),
                    Map.entry("camunda.operate.database", "elasticsearch"),
                    Map.entry("camunda.tasklist.database", "elasticsearch"),
                    // url
                    Map.entry(
                        "camunda.data.secondary-storage.elasticsearch.url",
                        "http://new-mismatching-url:4321"),
                    Map.entry("camunda.database.url", "http://legacy-mismatching-url:4321"),
                    Map.entry(
                        "camunda.tasklist.elasticsearch.url", "http://legacy-mismatching-url:4321"),
                    Map.entry(
                        "camunda.operate.elasticsearch.url",
                        "http://legacy-mismatching-url:4321"))));
    assertThat(context).hasFailed();
    assertThat(context.getStartupFailure())
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Ambiguous configuration")
        .hasMessageContaining("conflicting");
  }
}
