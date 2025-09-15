/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Backup;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.Security;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.SslProperties;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

    pouplateFromBackup(override);

    final SecondaryStorage database =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    if (SecondaryStorageType.elasticsearch.equals(database.getType())) {
      populateFromElasticsearch(override, database);
    } else if (SecondaryStorageType.opensearch == database.getType()) {
      populateFromOpensearch(override, database);
    }

    return override;
  }

  private void pouplateFromBackup(final OperateProperties override) {
    final Backup operateBackup =
        unifiedConfiguration.getCamunda().getData().getBackup().withOperateBackupProperties();
    final BackupProperties backupProperties = override.getBackup();
    backupProperties.setRepositoryName(operateBackup.getRepositoryName());
    backupProperties.setSnapshotTimeout(operateBackup.getSnapshotTimeout());
    backupProperties.setIncompleteCheckTimeoutInSeconds(
        operateBackup.getIncompleteCheckTimeout().getSeconds());
  }

  private void populateFromElasticsearch(
      final OperateProperties override, final SecondaryStorage database) {
    override.setDatabase(DatabaseType.Elasticsearch);
    override.getElasticsearch().setUrl(database.getElasticsearch().getUrl());
    override.getElasticsearch().setUsername(database.getElasticsearch().getUsername());
    override.getElasticsearch().setPassword(database.getElasticsearch().getPassword());
    override.getElasticsearch().setClusterName(database.getElasticsearch().getClusterName());
    override.getElasticsearch().setIndexPrefix(database.getElasticsearch().getIndexPrefix());

    override.getZeebeElasticsearch().setUrl(database.getElasticsearch().getUrl());

    populateFromSecurity(
        database.getElasticsearch().getSecurity(),
        override.getElasticsearch()::getSsl,
        override.getElasticsearch()::setSsl);
  }

  private void populateFromOpensearch(
      final OperateProperties override, final SecondaryStorage database) {
    override.setDatabase(DatabaseType.Opensearch);
    override.getOpensearch().setUrl(database.getOpensearch().getUrl());
    override.getOpensearch().setUsername(database.getOpensearch().getUsername());
    override.getOpensearch().setPassword(database.getOpensearch().getPassword());
    override.getOpensearch().setClusterName(database.getOpensearch().getClusterName());
    override.getOpensearch().setIndexPrefix(database.getOpensearch().getIndexPrefix());

    override.getZeebeOpensearch().setUrl(database.getOpensearch().getUrl());

    populateFromSecurity(
        database.getOpensearch().getSecurity(),
        override.getOpensearch()::getSsl,
        override.getOpensearch()::setSsl);
  }

  private void populateFromSecurity(
      final Security security,
      final Supplier<SslProperties> getSsl,
      final Consumer<SslProperties> setSsl) {
    final SslProperties sslProps = Objects.requireNonNullElseGet(getSsl.get(), SslProperties::new);
    sslProps.setCertificatePath(security.getCertificatePath());
    sslProps.setVerifyHostname(security.isVerifyHostname());
    sslProps.setSelfSigned(security.isSelfSigned());
    setSsl.accept(sslProps);
  }
}
