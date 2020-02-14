/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;

@AllArgsConstructor
public class DeleteIndexIfExistsStep implements UpgradeStep {
  private final String aliasName;
  private final int indexVersion;

  public DeleteIndexIfExistsStep(final IndexMappingCreator index) {
    this.aliasName = index.getIndexName();
    this.indexVersion = index.getVersion();
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {
    final OptimizeIndexNameService indexNameService = esIndexAdjuster.getIndexNameService();
    final String fullIndexName = OptimizeIndexNameService.getOptimizeIndexNameForAliasAndVersion(
      indexNameService.getOptimizeIndexAliasForIndex(aliasName), String.valueOf(indexVersion)
    );
    if (esIndexAdjuster.indexExists(fullIndexName)) {
      esIndexAdjuster.deleteIndex(fullIndexName);
    }
  }
}
