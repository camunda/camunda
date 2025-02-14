/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.util;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.TransportOptionsProvider;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.schema.OpenSearchMetadataService;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.mapper.OptimizeObjectMapper;
import io.camunda.optimize.upgrade.es.ElasticsearchClientBuilder;
import io.camunda.optimize.upgrade.os.OpenSearchClientBuilder;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;

/** Bunch of utility methods that might be required during upgrade operation. */
public final class UpgradeUtil {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(UpgradeUtil.class);

  private UpgradeUtil() {}

  public static UpgradeExecutionDependencies createUpgradeDependencies(
      final DatabaseType databaseType) {
    return createUpgradeDependenciesWithAdditionalConfigLocation(databaseType, (String[]) null);
  }

  public static UpgradeExecutionDependencies createUpgradeDependenciesWithAdditionalConfigLocation(
      final DatabaseType databaseType, final String... configLocations) {
    final ConfigurationService configurationService;
    if (configLocations == null || configLocations.length == 0) {
      configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    } else {
      configurationService =
          ConfigurationServiceBuilder.createConfigurationWithDefaultAndAdditionalLocations(
              configLocations);
    }
    return createUpgradeDependenciesWithAConfigurationService(databaseType, configurationService);
  }

  public static UpgradeExecutionDependencies createUpgradeDependenciesWithAConfigurationService(
      final DatabaseType databaseType, final ConfigurationService configurationService) {
    final OptimizeIndexNameService indexNameService =
        new OptimizeIndexNameService(configurationService, databaseType);
    if (databaseType.equals(DatabaseType.ELASTICSEARCH)) {
      final OptimizeElasticsearchClient esClient =
          new OptimizeElasticsearchClient(
              ElasticsearchClientBuilder.restClient(configurationService, new PluginRepository()),
              OptimizeObjectMapper.OPTIMIZE_MAPPER,
              ElasticsearchClientBuilder.build(
                  configurationService,
                  OptimizeObjectMapper.OPTIMIZE_MAPPER,
                  new PluginRepository()),
              indexNameService,
              new TransportOptionsProvider(configurationService));
      final ElasticSearchMetadataService metadataService =
          new ElasticSearchMetadataService(OptimizeObjectMapper.OPTIMIZE_MAPPER);
      return new UpgradeExecutionDependencies(
          databaseType,
          configurationService,
          indexNameService,
          esClient,
          OptimizeObjectMapper.OPTIMIZE_MAPPER,
          metadataService);
    } else {
      final OptimizeOpenSearchClient osClient =
          new OptimizeOpenSearchClient(
              OpenSearchClientBuilder.buildOpenSearchClientFromConfig(
                  configurationService, new PluginRepository()),
              OpenSearchClientBuilder.buildOpenSearchAsyncClientFromConfig(
                  configurationService, new PluginRepository()),
              indexNameService);
      final DatabaseMetadataService<OptimizeOpenSearchClient> metadataService =
          new OpenSearchMetadataService(OptimizeObjectMapper.OPTIMIZE_MAPPER);
      return new UpgradeExecutionDependencies(
          databaseType,
          configurationService,
          indexNameService,
          osClient,
          OptimizeObjectMapper.OPTIMIZE_MAPPER,
          metadataService);
    }
  }

  public static String readClasspathFileAsString(final String filePath) {
    String data = null;
    try (final InputStream inputStream =
        UpgradeUtil.class.getClassLoader().getResourceAsStream(filePath)) {
      data = readFromInputStream(inputStream);
    } catch (final IOException e) {
      LOG.error("can't read [{}] from classpath", filePath, e);
    }
    return data;
  }

  private static String readFromInputStream(final InputStream inputStream) throws IOException {
    try (final ByteArrayOutputStream result = new ByteArrayOutputStream()) {
      final byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }

      return result.toString("UTF-8");
    }
  }
}
