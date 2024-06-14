/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.upgrade.steps.schema;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
public class DeleteIndexIfExistsStep extends UpgradeStep {

  // This should be the name of the index without prefix and without version suffix
  @Getter private final String indexName;
  @Getter private final int indexVersion;

  public DeleteIndexIfExistsStep(final String indexName, final int indexVersion) {
    super(null);
    this.indexName = indexName;
    this.indexVersion = indexVersion;
  }

  @Override
  public IndexMappingCreator getIndex() {
    throw new UpgradeRuntimeException("Index class does not exist as it is being deleted");
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    final OptimizeIndexNameService indexNameService = schemaUpgradeClient.getIndexNameService();
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexName);
    schemaUpgradeClient.getAliasMap(indexAlias).keySet().stream()
        .filter(indexName -> indexName.contains(this.indexName))
        .forEach(schemaUpgradeClient::deleteIndexIfExists);
  }

  public String getVersionedIndexName() {
    return OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
        indexName, String.valueOf(indexVersion));
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.SCHEMA_DELETE_INDEX;
  }
}
