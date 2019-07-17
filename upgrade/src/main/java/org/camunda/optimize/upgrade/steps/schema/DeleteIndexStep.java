/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;


public class DeleteIndexStep implements UpgradeStep {
  private final String indexVersion;
  private final String typeName;

  public DeleteIndexStep(final String indexVersion,
                         final String typeName) {
    this.indexVersion = indexVersion;
    this.typeName = typeName;
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {
    final OptimizeIndexNameService indexNameService = esIndexAdjuster.getIndexNameService();
    final String indexAlias = indexNameService.getOptimizeIndexAliasForType(typeName);
    esIndexAdjuster.deleteIndex(indexNameService.getOptimizeIndexNameForAliasAndVersion(indexAlias, indexVersion));
  }
}
