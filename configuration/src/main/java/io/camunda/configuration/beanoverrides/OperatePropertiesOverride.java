/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.DocumentBasedSecondaryStorageBackup;
import io.camunda.configuration.InterceptorPlugin;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.SecondaryStorageSecurity;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.ProxyProperties;
import io.camunda.operate.property.SslProperties;
import io.camunda.search.connect.plugin.PluginConfiguration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
      populateFromElasticsearch(override, database);
      populateFromBackup(override, database.getElasticsearch().getBackup());
    } else if (SecondaryStorageType.opensearch == database.getType()) {
      populateFromOpensearch(override, database);
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

  private void populateFromElasticsearch(
      final OperateProperties override, final SecondaryStorage database) {
    override.setDatabase(DatabaseType.Elasticsearch);
    override.getElasticsearch().setUrl(database.getElasticsearch().getUrl());
    override.getElasticsearch().setUrls(database.getElasticsearch().getUrls());
    override.getElasticsearch().setUsername(database.getElasticsearch().getUsername());
    override.getElasticsearch().setPassword(database.getElasticsearch().getPassword());
    override.getElasticsearch().setClusterName(database.getElasticsearch().getClusterName());
    override.getElasticsearch().setIndexPrefix(database.getElasticsearch().getIndexPrefix());
    override.getElasticsearch().setDateFormat(database.getElasticsearch().getDateFormat());
    final var esProxy = new ProxyProperties();
    BeanUtils.copyProperties(database.getElasticsearch().getProxy(), esProxy);
    override.getElasticsearch().setProxy(esProxy);
    final var socketTimeout = database.getElasticsearch().getSocketTimeout();
    if (socketTimeout != null) {
      override.getElasticsearch().setSocketTimeout(Math.toIntExact(socketTimeout.toMillis()));
    }
    final var connectionTimeout = database.getElasticsearch().getConnectionTimeout();
    if (connectionTimeout != null) {
      override.getElasticsearch().setConnectTimeout(Math.toIntExact(connectionTimeout.toMillis()));
    }

    populateFromSecurity(
        database.getElasticsearch().getSecurity(),
        override.getElasticsearch()::getSsl,
        override.getElasticsearch()::setSsl);

    populateFromInterceptorPlugins(
        database.getElasticsearch().getInterceptorPlugins(),
        override.getElasticsearch()::getInterceptorPlugins,
        override.getElasticsearch()::setInterceptorPlugins,
        override.getDatabase());
  }

  private void populateFromOpensearch(
      final OperateProperties override, final SecondaryStorage database) {
    override.setDatabase(DatabaseType.Opensearch);
    override.getOpensearch().setUrl(database.getOpensearch().getUrl());
    override.getOpensearch().setUrls(database.getOpensearch().getUrls());
    override.getOpensearch().setUsername(database.getOpensearch().getUsername());
    override.getOpensearch().setPassword(database.getOpensearch().getPassword());
    override.getOpensearch().setClusterName(database.getOpensearch().getClusterName());
    override.getOpensearch().setIndexPrefix(database.getOpensearch().getIndexPrefix());
    override.getOpensearch().setDateFormat(database.getOpensearch().getDateFormat());
    final var osProxy = new ProxyProperties();
    BeanUtils.copyProperties(database.getOpensearch().getProxy(), osProxy);
    override.getOpensearch().setProxy(osProxy);
    final var socketTimeout = database.getOpensearch().getSocketTimeout();
    if (socketTimeout != null) {
      override.getOpensearch().setSocketTimeout(Math.toIntExact(socketTimeout.toMillis()));
    }
    final var connectionTimeout = database.getOpensearch().getConnectionTimeout();
    if (connectionTimeout != null) {
      override.getOpensearch().setConnectTimeout(Math.toIntExact(connectionTimeout.toMillis()));
    }

    populateFromSecurity(
        database.getOpensearch().getSecurity(),
        override.getOpensearch()::getSsl,
        override.getOpensearch()::setSsl);

    populateFromInterceptorPlugins(
        database.getOpensearch().getInterceptorPlugins(),
        override.getOpensearch()::getInterceptorPlugins,
        override.getOpensearch()::setInterceptorPlugins,
        override.getDatabase());
  }

  private void populateFromSecurity(
      final SecondaryStorageSecurity security,
      final Supplier<SslProperties> getSsl,
      final Consumer<SslProperties> setSsl) {
    final SslProperties sslProps = Objects.requireNonNullElseGet(getSsl.get(), SslProperties::new);
    sslProps.setCertificatePath(security.getCertificatePath());
    sslProps.setVerifyHostname(security.isVerifyHostname());
    sslProps.setSelfSigned(security.isSelfSigned());
    setSsl.accept(sslProps);
  }

  private void populateFromInterceptorPlugins(
      final List<InterceptorPlugin> interceptorPlugins,
      final Supplier<List<PluginConfiguration>> getPluginConfiguration,
      final Consumer<List<PluginConfiguration>> setPluginConfiguration,
      final DatabaseType databaseType) {

    // Log common interceptor plugins warning instead of using UnifiedConfigurationHelper logging.
    if (getPluginConfiguration.get() != null) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.data.secondary-storage."
                  + databaseType.toString().toLowerCase()
                  + ".interceptor-plugins",
              "camunda.operate." + databaseType.toString().toLowerCase() + ".interceptorPlugins");
      LOGGER.warn(warningMessage);
    }

    if (!interceptorPlugins.isEmpty()) {
      setPluginConfiguration.accept(
          interceptorPlugins.stream().map(InterceptorPlugin::toPluginConfiguration).toList());
    }
  }
}
