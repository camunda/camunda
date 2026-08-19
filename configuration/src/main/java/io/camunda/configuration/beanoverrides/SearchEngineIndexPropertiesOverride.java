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
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beans.SearchEngineIndexProperties;

public class SearchEngineIndexPropertiesOverride {

  public static void applyTo(final Camunda camunda, final SearchEngineIndexProperties override) {
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();

    final DocumentBasedSecondaryStorageDatabase database =
        (secondaryStorage.getType() == SecondaryStorageType.elasticsearch)
            ? secondaryStorage.getElasticsearch()
            : secondaryStorage.getOpensearch();

    override.setNumberOfShards(database.getNumberOfShards());
    override.setNumberOfReplicas(database.getNumberOfReplicas());
    override.setVariableSizeThreshold(database.getVariableSizeThreshold());
    override.setRefreshInterval(database.getRefreshInterval());

    override.setTemplatePriority(database.getTemplatePriority());
    if (!database.getNumberOfReplicasPerIndex().isEmpty()) {
      override.setReplicasByIndexName(database.getNumberOfReplicasPerIndex());
    }
    if (!database.getNumberOfShardsPerIndex().isEmpty()) {
      override.setShardsByIndexName(database.getNumberOfShardsPerIndex());
    }
    if (!database.getRefreshIntervalByIndexName().isEmpty()) {
      override.setRefreshIntervalByIndexName(database.getRefreshIntervalByIndexName());
    }
  }
}
