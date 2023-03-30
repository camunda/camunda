/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import lombok.EqualsAndHashCode;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;

@EqualsAndHashCode(callSuper = true)
public class DeleteIndexIfExistsStep extends UpgradeStep {

  public DeleteIndexIfExistsStep(final IndexMappingCreator index) {
    super(index);
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.SCHEMA_DELETE_INDEX;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    final OptimizeIndexNameService indexNameService = schemaUpgradeClient.getIndexNameService();
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(index);
    schemaUpgradeClient.getAliasMap(indexAlias).keySet().stream()
      .filter(indexName -> indexName.contains(index.getIndexName()))
      .forEach(schemaUpgradeClient::deleteIndexIfExists);
  }
}
