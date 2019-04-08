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

public class UpdateIndexStep implements UpgradeStep {
  private final String typeName;
  private final Integer targetVersion;
  private final String customMapping;

  public UpdateIndexStep(final String typeName,
                         final Integer targetVersion) {
    this(typeName, targetVersion, null);
  }

  public UpdateIndexStep(final String typeName,
                         final Integer targetVersion,
                         final String customMapping) {
    this.typeName = typeName;
    this.targetVersion = targetVersion;
    this.customMapping = customMapping;
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {
    final String indexAlias = getOptimizeIndexAliasForType(typeName);
    String sourceVersionAsString = String.valueOf(targetVersion - 1);
    String targetVersionAsString = String.valueOf(targetVersion);
    final String sourceIndexName = getOptimizeIndexNameForAliasAndVersion(indexAlias, sourceVersionAsString);
    final String targetIndexName = getOptimizeIndexNameForAliasAndVersion(indexAlias, targetVersionAsString);

    String indexSettingsAndMappings = esIndexAdjuster.getIndexMappings(sourceIndexName);
    indexSettingsAndMappings = customMapping != null ? customMapping : indexSettingsAndMappings;

    // create new index and reindex data to it
    esIndexAdjuster.createIndex(targetIndexName, indexSettingsAndMappings);
    esIndexAdjuster.reindex(sourceIndexName, targetIndexName, typeName, typeName);
    esIndexAdjuster.addAlias(targetIndexName, indexAlias);
    esIndexAdjuster.deleteIndex(sourceIndexName);
  }

}
