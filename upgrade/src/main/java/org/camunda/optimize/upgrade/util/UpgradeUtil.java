/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Bunch of utility methods that might be required during upgrade
 * operation.
 */
@Slf4j
public class UpgradeUtil {

  public static UpgradeExecutionDependencies createUpgradeDependencies() {
    ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    ObjectMapper objectMapper = new ObjectMapperFactory(
      new OptimizeDateTimeFormatterFactory().getObject(),
      ConfigurationServiceBuilder.createDefaultConfiguration()
    ).createOptimizeMapper();
    OptimizeIndexNameService indexNameService = new OptimizeIndexNameService(configurationService);
    OptimizeElasticsearchClient client = new OptimizeElasticsearchClient(
      ElasticsearchHighLevelRestClientBuilder.build(configurationService),
      indexNameService
    );
    ElasticsearchMetadataService metadataService = new ElasticsearchMetadataService(objectMapper);
    return new UpgradeExecutionDependencies(
      configurationService,
      indexNameService,
      client,
      objectMapper,
      metadataService
    );
  }

  public static String readClasspathFileAsString(String filePath) {
    InputStream inputStream = UpgradeUtil.class.getClassLoader().getResourceAsStream(filePath);
    String data = null;
    try {
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
