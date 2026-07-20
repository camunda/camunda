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
import io.camunda.configuration.UnifiedConfigurationHelper;
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
    return new PhysicalTenantSearchEngineConfigurations()
        .searchEngineConfigurationsByTenant(
            PhysicalTenantResolver.of(new MockEnvironment(), camunda),
            new SearchEngineConnectProperties(),
            new SearchEngineIndexProperties(),
            new SearchEngineRetentionProperties(),
            new SearchEngineSchemaManagerProperties());
  }

  @Test
  void shouldDisableSchemaCreationForNoneType() {
    // given
    final SearchEngineConnectProperties connect = new SearchEngineConnectProperties();
    connect.setType("none");
    final SearchEngineSchemaManagerProperties schemaManager =
        new SearchEngineSchemaManagerProperties();
    schemaManager.setCreateSchema(true);
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);

    // when
    final Map<String, SearchEngineConfiguration> configsByTenant =
        new PhysicalTenantSearchEngineConfigurations()
            .searchEngineConfigurationsByTenant(
                PhysicalTenantResolver.of(new MockEnvironment(), camunda),
                connect,
                new SearchEngineIndexProperties(),
                new SearchEngineRetentionProperties(),
                schemaManager);

    // then
    assertThat(configsByTenant.get("default").schemaManager().isCreateSchema()).isFalse();
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
