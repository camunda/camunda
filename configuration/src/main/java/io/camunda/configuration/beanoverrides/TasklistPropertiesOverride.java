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
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.tasklist.property.ProxyProperties;
import io.camunda.tasklist.property.SslProperties;
import io.camunda.tasklist.property.TasklistProperties;
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
@EnableConfigurationProperties(LegacyTasklistProperties.class)
@PropertySource("classpath:tasklist-version.properties")
@DependsOn("unifiedConfigurationHelper")
public class TasklistPropertiesOverride {

  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistPropertiesOverride.class);
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

    final SecondaryStorage database =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    if (SecondaryStorageType.elasticsearch == database.getType()) {
      populateFromElasticsearch(override, database);
      populateFromBackup(override, database.getElasticsearch().getBackup());
    } else if (SecondaryStorageType.opensearch == database.getType()) {
      populateFromOpensearch(override, database);
      populateFromBackup(override, database.getOpensearch().getBackup());
    }

    return override;
  }

  private void populateFromBackup(
      final TasklistProperties override, final DocumentBasedSecondaryStorageBackup backup) {
    override.getBackup().setRepositoryName(backup.getRepositoryName());
  }

  private void populateFromElasticsearch(
      final TasklistProperties override, final SecondaryStorage database) {
    override.setDatabase("elasticsearch");
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
      final TasklistProperties override, final SecondaryStorage database) {
    override.setDatabase("opensearch");
    override.getOpenSearch().setUrl(database.getOpensearch().getUrl());
    override.getOpenSearch().setUrls(database.getOpensearch().getUrls());
    override.getOpenSearch().setUsername(database.getOpensearch().getUsername());
    override.getOpenSearch().setPassword(database.getOpensearch().getPassword());
    override.getOpenSearch().setClusterName(database.getOpensearch().getClusterName());
    override.getOpenSearch().setIndexPrefix(database.getOpensearch().getIndexPrefix());
    override.getOpenSearch().setDateFormat(database.getOpensearch().getDateFormat());
    final var osProxy = new ProxyProperties();
    BeanUtils.copyProperties(database.getOpensearch().getProxy(), osProxy);
    override.getOpenSearch().setProxy(osProxy);
    final var socketTimeout = database.getOpensearch().getSocketTimeout();
    if (socketTimeout != null) {
      override.getOpenSearch().setSocketTimeout(Math.toIntExact(socketTimeout.toMillis()));
    }
    final var connectionTimeout = database.getOpensearch().getConnectionTimeout();
    if (connectionTimeout != null) {
      override.getOpenSearch().setConnectTimeout(Math.toIntExact(connectionTimeout.toMillis()));
    }

    populateFromSecurity(
        database.getOpensearch().getSecurity(),
        override.getOpenSearch()::getSsl,
        override.getOpenSearch()::setSsl);

    populateFromInterceptorPlugins(
        database.getOpensearch().getInterceptorPlugins(),
        override.getOpenSearch()::getInterceptorPlugins,
        override.getOpenSearch()::setInterceptorPlugins,
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
      final String databaseType) {

    // Log common interceptor plugins warning instead of using UnifiedConfigurationHelper logging.
    if (getPluginConfiguration.get() != null) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.data.secondary-storage."
                  + databaseType.toLowerCase()
                  + ".interceptor-plugins",
              "camunda.tasklist." + databaseType.toLowerCase() + ".interceptorPlugins");
      LOGGER.warn(warningMessage);
    }

    if (!interceptorPlugins.isEmpty()) {
      setPluginConfiguration.accept(
          interceptorPlugins.stream().map(InterceptorPlugin::toPluginConfiguration).toList());
    }
  }
}
