/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;

@AllArgsConstructor
public class DeleteIndexIfExistsStep implements UpgradeStep {

  private final IndexMappingCreator index;

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    final OptimizeIndexNameService indexNameService = schemaUpgradeClient.getIndexNameService();
    final String fullIndexName = indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(index);
    if (schemaUpgradeClient.indexExists(fullIndexName)) {
      schemaUpgradeClient.deleteIndex(fullIndexName);
    }
  }
}
