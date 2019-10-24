/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexNameForAliasAndVersion;

public class UpdateIndexStep implements UpgradeStep {
  private final IndexMappingCreator index;
  private final String mappingScript;

  public UpdateIndexStep(final IndexMappingCreator index, final String mappingScript) {
    this.index = index;
    this.mappingScript = mappingScript;
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {
    String indexName = index.getIndexName();
    int targetVersion = index.getVersion();
    final OptimizeIndexNameService indexNameService = esIndexAdjuster.getIndexNameService();
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexName);
    String sourceVersionAsString = String.valueOf(targetVersion - 1);
    String targetVersionAsString = String.valueOf(targetVersion);
    final String sourceIndexName = getOptimizeIndexNameForAliasAndVersion(
      indexAlias, sourceVersionAsString
    );
    final String targetIndexName = getOptimizeIndexNameForAliasAndVersion(
      indexAlias, targetVersionAsString
    );

    // create new index and reindex data to it
    esIndexAdjuster.createIndex(index);
    esIndexAdjuster.reindex(sourceIndexName, targetIndexName, index.getIndexName(), mappingScript);
    esIndexAdjuster.addAlias(index);
    esIndexAdjuster.deleteIndex(sourceIndexName);
  }

}
