/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexNameForAliasAndVersion;


public class DeleteIndexStep implements UpgradeStep {
  private final String indexVersion;
  private final String typeName;

  public DeleteIndexStep(final String indexVersion,
                         final String typeName) {
    this.indexVersion = indexVersion;
    this.typeName = typeName;
  }

  @Override
  public void execute(final ESIndexAdjuster ESIndexAdjuster) {
    final String indexAlias = getOptimizeIndexAliasForType(typeName);
    ESIndexAdjuster.deleteIndex(getOptimizeIndexNameForAliasAndVersion(indexAlias, indexVersion));
  }
}
