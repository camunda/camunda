/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.util;

import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;

import io.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import io.camunda.optimize.plugin.PluginJarFileLoader;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.RequestOptionsProvider;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Bunch of utility methods that might be required during upgrade operation. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class UpgradeUtil {

  public static UpgradeExecutionDependencies createUpgradeDependencies() {
    return createUpgradeDependenciesWithAdditionalConfigLocation((String[]) null);
  }

  public static UpgradeExecutionDependencies createUpgradeDependenciesWithAdditionalConfigLocation(
      final String... configLocations) {
    ConfigurationService configurationService;
    if (configLocations == null || configLocations.length == 0) {
      configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    } else {
      configurationService =
          ConfigurationServiceBuilder.createConfigurationWithDefaultAndAdditionalLocations(
              configLocations);
    }
    return createUpgradeDependenciesWithAConfigurationService(configurationService);
  }

  public static UpgradeExecutionDependencies createUpgradeDependenciesWithAConfigurationService(
      final ConfigurationService configurationService) {
    OptimizeIndexNameService indexNameService =
        new OptimizeIndexNameService(configurationService, DatabaseType.ELASTICSEARCH);
    ElasticsearchCustomHeaderProvider customHeaderProvider =
        new ElasticsearchCustomHeaderProvider(
            configurationService, new PluginJarFileLoader(configurationService));
    customHeaderProvider.initPlugins();
    OptimizeElasticsearchClient esClient =
        new OptimizeElasticsearchClient(
            ElasticsearchHighLevelRestClientBuilder.build(configurationService),
            indexNameService,
            new RequestOptionsProvider(customHeaderProvider.getPlugins(), configurationService),
            OPTIMIZE_MAPPER);
    ElasticSearchMetadataService metadataService =
        new ElasticSearchMetadataService(OPTIMIZE_MAPPER);
    return new UpgradeExecutionDependencies(
        configurationService, indexNameService, esClient, OPTIMIZE_MAPPER, metadataService);
  }

  public static String readClasspathFileAsString(String filePath) {
    String data = null;
    try (InputStream inputStream =
        UpgradeUtil.class.getClassLoader().getResourceAsStream(filePath)) {
      data = readFromInputStream(inputStream);
    } catch (IOException e) {
      log.error("can't read [{}] from classpath", filePath, e);
    }
    return data;
  }

  private static String readFromInputStream(InputStream inputStream) throws IOException {
    try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }

      return result.toString("UTF-8");
    }
  }
}
