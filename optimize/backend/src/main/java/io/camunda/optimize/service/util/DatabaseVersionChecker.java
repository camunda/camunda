/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.upgrade.es.ElasticsearchClientBuilder.getCurrentESVersion;
import static io.camunda.optimize.upgrade.os.OpenSearchClientBuilder.getCurrentOSVersion;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.TransportOptions;
import com.vdurmont.semver4j.Semver;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseVersionChecker {

  public static final String MIN_ES_SUPPORTED_VERSION = "8.13.0";
  public static final String MIN_OS_SUPPORTED_VERSION = "2.9.0";
  private static final Logger LOG = LoggerFactory.getLogger(DatabaseVersionChecker.class);

  public static void checkDatabaseVersionSupported(
      final String databaseVersion, final DatabaseType databaseVendor) {
    switch (databaseVendor) {
      case ELASTICSEARCH ->
          checkCurrentDBVersionIsHigherThanMinimum(databaseVersion, MIN_ES_SUPPORTED_VERSION);
      case OPENSEARCH ->
          checkCurrentDBVersionIsHigherThanMinimum(databaseVersion, MIN_OS_SUPPORTED_VERSION);
      default -> throw new IllegalStateException("Unexpected database version: " + databaseVendor);
    }
  }

  public static void checkESVersionSupport(
      final ElasticsearchClient esClient, final TransportOptions transportOptions)
      throws IOException {
    final String currentESVersion = getCurrentESVersion(esClient, transportOptions);
    checkCurrentDBVersionIsHigherThanMinimum(currentESVersion, MIN_ES_SUPPORTED_VERSION);
  }

  public static void checkOSVersionSupport(final OpenSearchClient osClient) throws IOException {
    final String currentOSVersion = getCurrentOSVersion(osClient);
    checkCurrentDBVersionIsHigherThanMinimum(currentOSVersion, MIN_OS_SUPPORTED_VERSION);
  }

  private static void checkCurrentDBVersionIsHigherThanMinimum(
      final String currentDBVersion, final String minSupportedDBVersion) {
    if (!new Semver(currentDBVersion).isGreaterThanOrEqualTo(new Semver(minSupportedDBVersion))) {
      throw new OptimizeRuntimeException(
          currentDBVersion + " version of Database is not supported by Optimize");
    }
  }
}
