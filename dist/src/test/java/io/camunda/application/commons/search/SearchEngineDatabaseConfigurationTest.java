/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineSchemaManagerPropertiesOverride;
import io.camunda.configuration.beans.LegacySearchEngineConnectProperties;
import io.camunda.configuration.beans.LegacySearchEngineIndexProperties;
import io.camunda.configuration.beans.LegacySearchEngineRetentionProperties;
import io.camunda.configuration.beans.LegacySearchEngineSchemaManagerProperties;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.configuration.beans.SearchEngineIndexProperties;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SearchEngineDatabaseConfigurationTest {

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  private static Map<String, SearchEngineConfiguration> configsByTenant(final Camunda camunda) {
    final UnifiedConfiguration unifiedConfig = new UnifiedConfiguration();
    final var connectOverride =
        new SearchEngineConnectPropertiesOverride(
            unifiedConfig, new LegacySearchEngineConnectProperties());
    final var indexOverride =
        new SearchEngineIndexPropertiesOverride(
            unifiedConfig, new LegacySearchEngineIndexProperties());
    final var retentionOverride =
        new SearchEngineRetentionPropertiesOverride(
            unifiedConfig, new LegacySearchEngineRetentionProperties());
    final var schemaManagerOverride =
        new SearchEngineSchemaManagerPropertiesOverride(
            unifiedConfig, new LegacySearchEngineSchemaManagerProperties());
    return new SearchEngineDatabaseConfiguration()
        .searchEngineConfigurationsByTenant(
            PhysicalTenantResolver.of(new MockEnvironment(), camunda),
            connectOverride.searchEngineConnectProperties(camunda),
            indexOverride.searchEngineIndexProperties(camunda),
            retentionOverride.searchEngineRetentionProperties(camunda),
            schemaManagerOverride.searchEngineSchemaManagerProperties(camunda));
  }

  @Test
  void shouldDisableSchemaCreationForNoneType() {
    // given
    final SearchEngineConnectProperties connect = new SearchEngineConnectProperties();
    connect.setType("none");
    final SearchEngineSchemaManagerProperties schemaManager =
        new SearchEngineSchemaManagerProperties();
    schemaManager.setCreateSchema(true);

    // when
    final SearchEngineConfiguration config =
        new SearchEngineDatabaseConfiguration()
            .searchEngineConfiguration(
                connect,
                new SearchEngineIndexProperties(),
                new SearchEngineRetentionProperties(),
                schemaManager);

    // then
    assertThat(config.schemaManager().isCreateSchema()).isFalse();
  }

  @Test
  void shouldKeepSchemaCreationForElasticsearchTenant() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);

    // when
    final Map<String, SearchEngineConfiguration> configs = configsByTenant(camunda);

    // then
    assertThat(configs.get("default").schemaManager().isCreateSchema()).isTrue();
  }
}
