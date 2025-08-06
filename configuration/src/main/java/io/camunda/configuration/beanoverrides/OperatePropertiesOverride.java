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
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties(LegacyOperateProperties.class)
@PropertySource("classpath:operate-version.properties")
@DependsOn("unifiedConfigurationHelper")
public class OperatePropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacyOperateProperties legacyOperateProperties;

  public OperatePropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacyOperateProperties legacyOperateProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyOperateProperties = legacyOperateProperties;
  }

  @Bean
  @Primary
  public OperateProperties operateProperties() {
    final OperateProperties override = new OperateProperties();
    BeanUtils.copyProperties(legacyOperateProperties, override);

    final SecondaryStorage database =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    if (SecondaryStorageType.elasticsearch.equals(database.getType())) {
      override.setDatabase(DatabaseType.Elasticsearch);
      override.getElasticsearch().setUrl(database.getElasticsearch().getUrl());
    } else if (SecondaryStorageType.opensearch == database.getType()) {
      override.setDatabase(DatabaseType.Opensearch);
    }

    // TODO: Populate the rest of the bean using unifiedConfiguration
    //  override.setSampleField(unifiedConfiguration.getSampleField());

    return override;
  }
}
