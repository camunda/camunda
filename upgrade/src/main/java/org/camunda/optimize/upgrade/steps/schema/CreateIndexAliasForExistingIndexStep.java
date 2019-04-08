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

public class CreateIndexAliasForExistingIndexStep implements UpgradeStep {
  private final String fromTypeName;
  private final String toTypeName;
  private final String targetVersion;
  private final String customMapping;

  public CreateIndexAliasForExistingIndexStep(final String fromTypeName,
                                              final String targetVersion) {
    this(fromTypeName, fromTypeName, targetVersion, null);
  }

  public CreateIndexAliasForExistingIndexStep(final String fromTypeName,
                                              final String toTypeName,
                                              final String targetVersion) {
    this(fromTypeName, toTypeName, targetVersion, null);
  }

  public CreateIndexAliasForExistingIndexStep(final String fromTypeName,
                                              final String toTypeName,
                                              final String targetVersion,
                                              final String customMapping) {
    this.fromTypeName = fromTypeName;
    this.toTypeName = toTypeName;
    this.targetVersion = targetVersion;
    this.customMapping = customMapping;
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {
    final String sourceIndexAlias = getOptimizeIndexAliasForType(fromTypeName);
    final String targetIndexAlias = getOptimizeIndexAliasForType(toTypeName);
    // when aliases are created there is no source index version yet
    final String sourceIndexName = getOptimizeIndexNameForAliasAndVersion(sourceIndexAlias, null);
    final String targetIndexName = getOptimizeIndexNameForAliasAndVersion(targetIndexAlias, targetVersion);

    String indexMappings = esIndexAdjuster.getIndexMappings(sourceIndexName);
    indexMappings = customMapping != null ? customMapping : indexMappings;

    // create new index and reindex data to it
    esIndexAdjuster.createIndex(targetIndexName, indexMappings);
    esIndexAdjuster.reindex(sourceIndexName, targetIndexName, fromTypeName, toTypeName);

    // delete the old index and create the alias
    esIndexAdjuster.deleteIndex(sourceIndexName);
    esIndexAdjuster.addAlias(targetIndexName, targetIndexAlias);
  }

}
