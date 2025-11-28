/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.LegacySearchEngineSchemaManagerProperties;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
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

    final boolean enableVersionCheck =
        unifiedConfiguration.getCamunda().getSystem().getUpgrade().getEnableVersionCheck();
    override.setVersionCheckRestrictionEnabled(enableVersionCheck);

    return override;
  }
}
