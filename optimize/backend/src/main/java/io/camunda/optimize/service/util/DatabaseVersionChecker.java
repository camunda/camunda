/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder.getCurrentESVersion;
import static io.camunda.optimize.upgrade.os.OpenSearchClientBuilder.getCurrentOSVersion;

import com.vdurmont.semver4j.Semver;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.opensearch.client.opensearch.OpenSearchClient;

@Slf4j
public class DatabaseVersionChecker {

  public static final String MIN_ES_SUPPORTED_VERSION = "8.13.0";
  public static final String MIN_OS_SUPPORTED_VERSION = "2.9.0";

  public static void checkESVersionSupport(
      final RestHighLevelClient esClient, final RequestOptions requestOptions) throws IOException {
    final String currentESVersion = getCurrentESVersion(esClient, requestOptions);
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
