/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.service;

import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;


public class ValidationService {
  private static final String ENVIRONMENT_CONFIG_FILE = "environment-config.yaml";
  private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

  private ElasticsearchMetadataService metadataService;
  private ConfigurationService configurationService;

  public ValidationService(final ConfigurationService configurationService,
                           final ElasticsearchMetadataService metadataService) {
    this.configurationService = configurationService;
    this.metadataService = metadataService;
  }

  public void validateConfiguration() {
    configurationService.validateNoDeprecatedConfigKeysUsed();
  }

  public void validateVersions(final RestHighLevelClient restClient, final String fromVersion, final String toVersion) {

    final String schemaVersion = metadataService.readMetadata(restClient)
      .orElseThrow(() -> new UpgradeRuntimeException("No Optimize Metadata present."))
      .getSchemaVersion();

    if (!fromVersion.equals(schemaVersion)) {
      throw new UpgradeRuntimeException(
        "Schema version saved in Metadata [" + schemaVersion + "] does not match required [" + fromVersion + "]"
      );
    }

    if (toVersion == null || toVersion.isEmpty()) {
      throw new UpgradeRuntimeException("New schema version is not allowed to be empty or null!");
    }
  }

  public void validateEnvironmentConfigInClasspath() {
    boolean configAvailable = false;
    try (InputStream resourceAsStream =
           ValidationService.class.getResourceAsStream("/" + ENVIRONMENT_CONFIG_FILE)) {
      if (resourceAsStream != null) {
        configAvailable = true;
      }
    } catch (IOException e) {
      logger.error("Can't resolve " + ENVIRONMENT_CONFIG_FILE, e);
    }

    if (!configAvailable) {
      throw new UpgradeRuntimeException(
        "Couldn't read " + ENVIRONMENT_CONFIG_FILE + " from environment folder in Optimize root!"
      );
    }
  }
}
