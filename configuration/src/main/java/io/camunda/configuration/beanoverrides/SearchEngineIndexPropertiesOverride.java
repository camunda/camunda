/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.LegacySearchEngineIndexProperties;
import io.camunda.configuration.beans.SearchEngineIndexProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(LegacySearchEngineIndexProperties.class)
@DependsOn("unifiedConfigurationHelper")
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SearchEngineIndexPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacySearchEngineIndexProperties legacySearchEngineIndexProperties;

  public SearchEngineIndexPropertiesOverride(
      @Autowired final UnifiedConfiguration unifiedConfiguration,
      @Autowired final LegacySearchEngineIndexProperties legacySearchEngineIndexProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacySearchEngineIndexProperties = legacySearchEngineIndexProperties;
  }

  @Bean
  @Primary
  public SearchEngineIndexProperties searchEngineIndexProperties() {
    final SearchEngineIndexProperties override = new SearchEngineIndexProperties();
    BeanUtils.copyProperties(legacySearchEngineIndexProperties, override);

    final SecondaryStorage secondaryStorage =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    final DocumentBasedSecondaryStorageDatabase database =
        (secondaryStorage.getType() == SecondaryStorageType.elasticsearch)
            ? secondaryStorage.getElasticsearch()
            : secondaryStorage.getOpensearch();

    override.setNumberOfShards(database.getNumberOfShards());

    return override;
  }
}
