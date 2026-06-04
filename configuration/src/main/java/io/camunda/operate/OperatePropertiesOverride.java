/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import io.camunda.configuration.DocumentBasedSecondaryStorageBackup;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beanoverrides.LegacyOperateProperties;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(OperatePropertiesOverride.class);
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
      populateFromBackup(override, database.getElasticsearch().getBackup());
    } else if (SecondaryStorageType.opensearch == database.getType()) {
      populateFromBackup(override, database.getOpensearch().getBackup());
    }

    return override;
  }

  private void populateFromBackup(
      final OperateProperties override, final DocumentBasedSecondaryStorageBackup backup) {
    final BackupProperties backupProperties = override.getBackup();
    backupProperties.setRepositoryName(backup.getRepositoryName());
    backupProperties.setSnapshotTimeout(backup.getSnapshotTimeout());
    backupProperties.setIncompleteCheckTimeoutInSeconds(
        backup.getIncompleteCheckTimeout().getSeconds());
  }
}
