/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;
import org.elasticsearch.index.query.QueryBuilder;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReindexStep extends UpgradeStep {
  @Getter
  private final IndexMappingCreator sourceIndex;
  @Getter
  private final IndexMappingCreator targetIndex;
  private final QueryBuilder sourceIndexFilterQuery;
  private final String mappingScript;

  public ReindexStep(final IndexMappingCreator sourceIndex, final IndexMappingCreator targetIndex,
                     final QueryBuilder sourceIndexFilterQuery) {
    this(sourceIndex, targetIndex, sourceIndexFilterQuery, null);
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.REINDEX;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.reindex(sourceIndex, targetIndex, sourceIndexFilterQuery, mappingScript);
  }

}
