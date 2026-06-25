/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.InterceptorPlugin;
import io.camunda.configuration.Opensearch;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.SecondaryStorageDatabase;
import io.camunda.configuration.SecondaryStorageSecurity;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.LegacySearchEngineConnectProperties;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(LegacySearchEngineConnectProperties.class)
@DependsOn("unifiedConfigurationHelper")
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch,
  SecondaryStorageType.rdbms
})
public class SearchEngineConnectPropertiesOverride {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SearchEngineConnectPropertiesOverride.class);

  private final Camunda camunda;
  private final LegacySearchEngineConnectProperties legacySearchEngineConnectProperties;

  public SearchEngineConnectPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacySearchEngineConnectProperties legacySearchEngineConnectProperties) {
    camunda = unifiedConfiguration.getCamunda();
    this.legacySearchEngineConnectProperties = legacySearchEngineConnectProperties;
  }

  @Bean
  @Primary
  public SearchEngineConnectProperties searchEngineConnectProperties() {
    final SearchEngineConnectProperties override = new SearchEngineConnectProperties();
    BeanUtils.copyProperties(legacySearchEngineConnectProperties, override);
    new Converter(camunda).applyTo(override);
    return override;
  }

  /** Maps a {@link Camunda} configuration into a {@link SearchEngineConnectProperties}. */
  public static final class Converter {

    private final Camunda camunda;

    public Converter(final Camunda camunda) {
      this.camunda = camunda;
    }

    public SearchEngineConnectProperties convert() {
      final SearchEngineConnectProperties override = new SearchEngineConnectProperties();
      applyTo(override);
      return override;
    }

    public void applyTo(final SearchEngineConnectProperties override) {
      final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();

      final SecondaryStorageDatabase<?> database;

      switch (secondaryStorage.getType()) {
        case elasticsearch:
          {
            database = secondaryStorage.getElasticsearch();
            populateFromDocumentBasedSecondaryStorageDatabase(
                (DocumentBasedSecondaryStorageDatabase) database, override);
            break;
          }
        case opensearch:
          {
            database = secondaryStorage.getOpensearch();
            populateFromDocumentBasedSecondaryStorageDatabase(
                (DocumentBasedSecondaryStorageDatabase) database, override);
            override.setAwsEnabled(((Opensearch) database).isAwsEnabled());
            break;
          }
        case rdbms:
          {
            database = secondaryStorage.getRdbms();
            break;
          }
        default:
          throw new IllegalStateException(
              "Unsupported secondary storage type: " + secondaryStorage.getType());
      }

      override.setType(secondaryStorage.getType().name());
      override.setUrl(database.getUrl());
      override.setUrls(database.getUrls());

      populateFromSecurity(override);
      populateFromInterceptorPlugins(override);

      override.setUsername(database.getUsername());
      override.setPassword(database.getPassword());
    }

    private void populateFromDocumentBasedSecondaryStorageDatabase(
        final DocumentBasedSecondaryStorageDatabase database,
        final SearchEngineConnectProperties override) {
      override.setClusterName(database.getClusterName());
      override.setDateFormat(database.getDateFormat());
      final var socketTimeout = database.getSocketTimeout();
      if (socketTimeout != null) {
        override.setSocketTimeout(Math.toIntExact(socketTimeout.toMillis()));
      }
      final var connectionTimeout = database.getConnectionTimeout();
      if (connectionTimeout != null) {
        override.setConnectTimeout(Math.toIntExact(connectionTimeout.toMillis()));
      }
      final var maxConnections = database.getMaxConnections();
      if (maxConnections != null) {
        override.setMaxConnections(maxConnections);
      }
      final var maxConnectionsPerRoute = database.getMaxConnectionsPerRoute();
      if (maxConnectionsPerRoute != null) {
        override.setMaxConnectionsPerRoute(maxConnectionsPerRoute);
      }
      override.setIndexPrefix(database.getIndexPrefix());
      override.setProxy(database.getProxy());
    }

    private void populateFromSecurity(final SearchEngineConnectProperties override) {
      final SecondaryStorageSecurity security = resolveSecurity(override);
      if (security == null) {
        return;
      }

      override.getSecurity().setEnabled(security.isEnabled());
      override.getSecurity().setCertificatePath(security.getCertificatePath());
      override.getSecurity().setVerifyHostname(security.isVerifyHostname());
      override.getSecurity().setSelfSigned(security.isSelfSigned());
    }

    private SecondaryStorageSecurity resolveSecurity(final SearchEngineConnectProperties override) {
      final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();

      return switch (override.getTypeEnum()) {
        case ELASTICSEARCH -> secondaryStorage.getElasticsearch().getSecurity();
        case OPENSEARCH -> secondaryStorage.getOpensearch().getSecurity();
        default -> null;
      };
    }

    private void populateFromInterceptorPlugins(final SearchEngineConnectProperties override) {
      final List<InterceptorPlugin> interceptorPlugins = resolveInterceptorPlugin(override);

      // Log common interceptor plugins warning instead of using UnifiedConfigurationHelper logging.
      if (override.getInterceptorPlugins() != null && !override.getInterceptorPlugins().isEmpty()) {
        final String warningMessage =
            String.format(
                "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
                "camunda.data.secondary-storage."
                    + override.getTypeEnum().toString().toLowerCase()
                    + ".interceptor-plugins",
                "camunda.database.interceptorPlugins");
        LOGGER.warn(warningMessage);
      }

      if (!interceptorPlugins.isEmpty()) {
        override.setInterceptorPlugins(
            interceptorPlugins.stream().map(InterceptorPlugin::toPluginConfiguration).toList());
      }
    }

    private List<InterceptorPlugin> resolveInterceptorPlugin(
        final SearchEngineConnectProperties override) {
      final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();

      return switch (override.getTypeEnum()) {
        case ELASTICSEARCH -> secondaryStorage.getElasticsearch().getInterceptorPlugins();
        case OPENSEARCH -> secondaryStorage.getOpensearch().getInterceptorPlugins();
        default -> Collections.emptyList();
      };
    }
  }
}
