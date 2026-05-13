/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.configuration.beans.SearchEngineIndexProperties;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.schema.config.SearchEngineConfiguration;

public final class SearchEngineConfigurationOverrides {

  private SearchEngineConfigurationOverrides() {}

  public static SearchEngineConfiguration forTenant(final Camunda tenantCamunda) {
    final SearchEngineConnectProperties connect =
        new SearchEngineConnectPropertiesOverride.Converter(tenantCamunda).convert();
    final SearchEngineIndexProperties index =
        new SearchEngineIndexPropertiesOverride.Converter(tenantCamunda).convert();
    final SearchEngineRetentionProperties retention =
        new SearchEngineRetentionPropertiesOverride.Converter(tenantCamunda).convert();
    final SearchEngineSchemaManagerProperties schemaManager =
        new SearchEngineSchemaManagerPropertiesOverride.Converter(tenantCamunda).convert();

    if (DatabaseConfig.NONE.equalsIgnoreCase(connect.getTypeEnum().name())) {
      schemaManager.setCreateSchema(false);
    }

    return SearchEngineConfiguration.of(
        b -> b.connect(connect).index(index).retention(retention).schemaManager(schemaManager));
  }
}
