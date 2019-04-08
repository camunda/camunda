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


public class CreateIndexStep implements UpgradeStep {
  private final String targetVersion;
  private final String typeName;
  private final String mapping;

  public CreateIndexStep(final String targetVersion,
                         final String typeName,
                         final String mapping) {
    this.targetVersion = targetVersion;
    this.typeName = typeName;
    this.mapping = mapping;
  }

  @Override
  public void execute(final ESIndexAdjuster ESIndexAdjuster) {
    final String indexAlias = getOptimizeIndexAliasForType(typeName);
    ESIndexAdjuster.createIndex(
      getOptimizeIndexNameForAliasAndVersion(indexAlias, targetVersion),
      targetVersion != null ? indexAlias : null,
      mapping
    );
  }
}
