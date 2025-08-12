/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.search.connect.configuration.DatabaseType;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(SearchEngineConnectProperties.class)
@DependsOn("unifiedConfigurationHelper")
public class SearchEngineConnectPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final SearchEngineConnectProperties legacyProperties;

  public SearchEngineConnectPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final SearchEngineConnectProperties legacyProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyProperties = legacyProperties;
  }

  @Bean
  @Primary
  public SearchEngineConnectProperties searchEngineConnectProperties() {
    final SearchEngineConnectProperties override = new SearchEngineConnectProperties();
    BeanUtils.copyProperties(legacyProperties, override);

    final SecondaryStorage database =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    if (SecondaryStorageType.elasticsearch == database.getType()) {
      override.setType(DatabaseType.ELASTICSEARCH.toString());
      override.setUrl(database.getElasticsearch().getUrl());
    } else if (SecondaryStorageType.opensearch == database.getType()) {
      override.setType(DatabaseType.OPENSEARCH.toString());
      override.setUrl(database.getOpensearch().getUrl());
    }

    // TODO: Populate the bean using unifiedConfiguration
    //  override.setSampleField(unifiedConfiguration.getSampleField());

    return override;
  }
}
