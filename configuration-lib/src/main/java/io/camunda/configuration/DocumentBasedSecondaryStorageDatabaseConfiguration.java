/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface DocumentBasedSecondaryStorageDatabaseConfiguration {

  String databaseName();

  String getUrl();

  List<String> getUrls();

  String getUsername();

  String getPassword();

  boolean isCreateSchema();

  String getClusterName();

  String getIndexPrefix();

  String getDateFormat();

  @Nullable String getRefreshInterval();

  int getNumberOfShards();

  int getNumberOfReplicas();

  int getVariableSizeThreshold();

  @Nullable Integer getTemplatePriority();

  Map<String, Integer> getNumberOfReplicasPerIndex();

  Map<String, Integer> getNumberOfShardsPerIndex();

  Map<String, String> getRefreshIntervalByIndexName();

  @Nullable Duration getSocketTimeout();

  @Nullable Duration getConnectionTimeout();
}
