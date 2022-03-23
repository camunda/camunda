/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.service;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static org.camunda.optimize.service.util.ESVersionChecker.checkESVersionSupport;

@AllArgsConstructor
@Slf4j
public class UpgradeValidationService {
  private static final String ENVIRONMENT_CONFIG_FILE = "environment-config.yaml";

  public void validateSchemaVersions(@NonNull final String schemaVersion,
                                     @NonNull final String fromVersion,
                                     @NonNull final String toVersion) {
    try {
      if (!(
        Objects.equals(fromVersion, schemaVersion)
          || Objects.equals(fromVersion, getMajorAndMinor(schemaVersion))
          || Objects.equals(toVersion, schemaVersion))
      ) {
        throw new UpgradeRuntimeException(
          String.format(
            "Schema version saved in Metadata [%s] must be one of [%s, %s]",
            schemaVersion, fromVersion, toVersion
          )
        );
      }
    } catch (final Exception e) {
      throw new UpgradeRuntimeException(e.getMessage(), e);
    }
  }

  public void validateESVersion(final OptimizeElasticsearchClient restClient,
                                final String toVersion) {
    try {
      checkESVersionSupport(restClient.getHighLevelClient(), restClient.requestOptions());
    } catch (Exception e) {
      String errorMessage =
        "It was not possible to upgrade Optimize to version " + toVersion + ".\n" +
          e.getMessage();
      throw new UpgradeRuntimeException(errorMessage);
    }
  }

  public void validateEnvironmentConfigInClasspath() {
    boolean configAvailable = false;
    try (InputStream resourceAsStream =
           UpgradeValidationService.class.getResourceAsStream("/" + ENVIRONMENT_CONFIG_FILE)) {
      if (resourceAsStream != null) {
        configAvailable = true;
      }
    } catch (IOException e) {
      log.error("Can't resolve " + ENVIRONMENT_CONFIG_FILE, e);
    }

    if (!configAvailable) {
      throw new UpgradeRuntimeException(
        "Couldn't read " + ENVIRONMENT_CONFIG_FILE + " from config folder in Optimize root!"
      );
    }
  }
}
