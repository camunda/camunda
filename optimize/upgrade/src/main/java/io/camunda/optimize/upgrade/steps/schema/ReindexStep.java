/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.schema;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.elasticsearch.index.query.QueryBuilder;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReindexStep extends UpgradeStep {
  @Getter private final IndexMappingCreator sourceIndex;
  @Getter private final IndexMappingCreator targetIndex;
  private final QueryBuilder sourceIndexFilterQuery;
  private final String mappingScript;

  public ReindexStep(
      final IndexMappingCreator sourceIndex,
      final IndexMappingCreator targetIndex,
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
