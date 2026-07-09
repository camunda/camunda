/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beans.LegacySearchEngineSchemaManagerProperties;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import java.util.Set;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(LegacySearchEngineSchemaManagerProperties.class)
@DependsOn("unifiedConfigurationHelper")
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SearchEngineSchemaManagerPropertiesOverride {

  private static final String HEALTH_CHECK_ENABLED_PROPERTY =
      "camunda.database.schema-manager.healthCheckEnabled";
  private static final Set<String> LEGACY_HEALTH_CHECK_ENABLED_PROPERTIES =
      Set.of(
          "camunda.operate.elasticsearch.healthCheckEnabled",
          "camunda.operate.opensearch.healthCheckEnabled",
          "camunda.tasklist.elasticsearch.healthCheckEnabled",
          "camunda.tasklist.openSearch.healthCheckEnabled");

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacySearchEngineSchemaManagerProperties legacySearchEngineSchemaManagerProperties;

  public SearchEngineSchemaManagerPropertiesOverride(
      @Autowired final UnifiedConfiguration unifiedConfiguration,
      @Autowired
          final LegacySearchEngineSchemaManagerProperties
              legacySearchEngineSchemaManagerProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacySearchEngineSchemaManagerProperties = legacySearchEngineSchemaManagerProperties;
  }

  @Bean
  @Primary
  public SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties() {
    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();
    BeanUtils.copyProperties(legacySearchEngineSchemaManagerProperties, override);
    applyTo(unifiedConfiguration.getCamunda(), override);
    return override;
  }

  public static void applyTo(
      final Camunda camunda, final SearchEngineSchemaManagerProperties override) {
    override.setVersionCheckRestrictionEnabled(
        camunda.getSystem().getUpgrade().getEnableVersionCheck());

    camunda
        .getData()
        .getSecondaryStorage()
        .elasticsearchOrOpensearch()
        .ifPresent(
            secondaryStorage -> {
              override.setPerformCleanup(secondaryStorage.isPerformCleanup());
              override.setCreateSchema(secondaryStorage.isCreateSchema());
            });

    override.setHealthCheckEnabled(
        UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
            HEALTH_CHECK_ENABLED_PROPERTY,
            override.isHealthCheckEnabled(),
            Boolean.class,
            SUPPORTED,
            LEGACY_HEALTH_CHECK_ENABLED_PROPERTIES));
  }
}
