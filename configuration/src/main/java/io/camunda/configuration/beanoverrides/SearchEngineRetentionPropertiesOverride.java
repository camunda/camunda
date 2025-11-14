/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.Retention;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.LegacySearchEngineRetentionProperties;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(LegacySearchEngineRetentionProperties.class)
@DependsOn("unifiedConfigurationHelper")
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SearchEngineRetentionPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacySearchEngineRetentionProperties legacySearchEngineRetentionProperties;

  public SearchEngineRetentionPropertiesOverride(
      @Autowired final UnifiedConfiguration unifiedConfiguration,
      @Autowired
          final LegacySearchEngineRetentionProperties legacySearchEngineRetentionProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacySearchEngineRetentionProperties = legacySearchEngineRetentionProperties;
  }

  @Bean
  @Primary
  public SearchEngineRetentionProperties searchEngineRetentionProperties() {
    final SearchEngineRetentionProperties override = new SearchEngineRetentionProperties();
    BeanUtils.copyProperties(legacySearchEngineRetentionProperties, override);

    populateFromRetention(override);
    populateFromSecondaryStorage(override);

    return override;
  }

  private void populateFromRetention(final SearchEngineRetentionProperties override) {
    final Retention retention =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getRetention();
    override.setEnabled(retention.isEnabled());
    override.setMinimumAge(retention.getMinimumAge());
  }

  private void populateFromSecondaryStorage(final SearchEngineRetentionProperties override) {
    final SecondaryStorage secondaryStorage =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    final DocumentBasedSecondaryStorageDatabase database =
        switch (secondaryStorage.getType()) {
          case elasticsearch -> secondaryStorage.getElasticsearch();
          case opensearch -> secondaryStorage.getOpensearch();
          default -> null;
        };

    if (database == null) {
      return;
    }

    override.setPolicyName(database.getHistory().getPolicyName());
  }
}
