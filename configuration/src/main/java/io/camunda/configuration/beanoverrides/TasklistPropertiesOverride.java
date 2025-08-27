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
import io.camunda.tasklist.property.BackupProperties;
import io.camunda.tasklist.property.SslProperties;
import io.camunda.tasklist.property.TasklistProperties;
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
@EnableConfigurationProperties(LegacyTasklistProperties.class)
@PropertySource("classpath:tasklist-version.properties")
@DependsOn("unifiedConfigurationHelper")
public class TasklistPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacyTasklistProperties legacyTasklistProperties;

  public TasklistPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacyTasklistProperties legacyTasklistProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyTasklistProperties = legacyTasklistProperties;
  }

  @Bean
  @Primary
  public TasklistProperties tasklistProperties() {
    final TasklistProperties override = new TasklistProperties();
    BeanUtils.copyProperties(legacyTasklistProperties, override);

    pouplateFromBackup(override);

    final SecondaryStorage database =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    if (SecondaryStorageType.elasticsearch == database.getType()) {
      populateFromElasticsearch(override, database);
      override.getElasticsearch().setClusterName(database.getElasticsearch().getClusterName());
    } else if (SecondaryStorageType.opensearch == database.getType()) {
      populateFromOpensearch(override, database);
      override.getOpenSearch().setClusterName(database.getOpensearch().getClusterName());
    }

    return override;
  }

  private void pouplateFromBackup(final TasklistProperties override) {
    final Backup backup =
        unifiedConfiguration.getCamunda().getData().getBackup().withTasklistBackupProperties();
    final BackupProperties backupProperties = override.getBackup();
    backupProperties.setRepositoryName(backup.getRepositoryName());
  }

  private void populateFromElasticsearch(
      final TasklistProperties override, final SecondaryStorage database) {
    override.setDatabase("elasticsearch");
    override.getElasticsearch().setUrl(database.getElasticsearch().getUrl());
    override.getZeebeElasticsearch().setUrl(database.getElasticsearch().getUrl());

    populateFromSecurity(
        database.getElasticsearch().getSecurity(),
        override.getElasticsearch()::getSsl,
        override.getElasticsearch()::setSsl);
  }

  private void populateFromOpensearch(
      final TasklistProperties override, final SecondaryStorage database) {
    override.setDatabase("opensearch");
    override.getOpenSearch().setUrl(database.getOpensearch().getUrl());
    override.getZeebeOpenSearch().setUrl(database.getOpensearch().getUrl());

    populateFromSecurity(
        database.getOpensearch().getSecurity(),
        override.getOpenSearch()::getSsl,
        override.getOpenSearch()::setSsl);
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
