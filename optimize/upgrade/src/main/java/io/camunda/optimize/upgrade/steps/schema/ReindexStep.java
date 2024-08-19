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
import org.elasticsearch.index.query.QueryBuilder;

public class ReindexStep extends UpgradeStep {

  private final IndexMappingCreator sourceIndex;
  private final IndexMappingCreator targetIndex;
  private final QueryBuilder sourceIndexFilterQuery;
  private final String mappingScript;

  public ReindexStep(
      final IndexMappingCreator sourceIndex,
      final IndexMappingCreator targetIndex,
      final QueryBuilder sourceIndexFilterQuery) {
    this(sourceIndex, targetIndex, sourceIndexFilterQuery, null);
  }

  public ReindexStep(
      final IndexMappingCreator sourceIndex,
      final IndexMappingCreator targetIndex,
      final QueryBuilder sourceIndexFilterQuery,
      final String mappingScript) {
    this.sourceIndex = sourceIndex;
    this.targetIndex = targetIndex;
    this.sourceIndexFilterQuery = sourceIndexFilterQuery;
    this.mappingScript = mappingScript;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.REINDEX;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.reindex(sourceIndex, targetIndex, sourceIndexFilterQuery, mappingScript);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ReindexStep;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $sourceIndex = sourceIndex;
    result = result * PRIME + ($sourceIndex == null ? 43 : $sourceIndex.hashCode());
    final Object $targetIndex = targetIndex;
    result = result * PRIME + ($targetIndex == null ? 43 : $targetIndex.hashCode());
    final Object $sourceIndexFilterQuery = sourceIndexFilterQuery;
    result =
        result * PRIME
            + ($sourceIndexFilterQuery == null ? 43 : $sourceIndexFilterQuery.hashCode());
    final Object $mappingScript = mappingScript;
    result = result * PRIME + ($mappingScript == null ? 43 : $mappingScript.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ReindexStep)) {
      return false;
    }
    final ReindexStep other = (ReindexStep) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$sourceIndex = sourceIndex;
    final Object other$sourceIndex = other.sourceIndex;
    if (this$sourceIndex == null
        ? other$sourceIndex != null
        : !this$sourceIndex.equals(other$sourceIndex)) {
      return false;
    }
    final Object this$targetIndex = targetIndex;
    final Object other$targetIndex = other.targetIndex;
    if (this$targetIndex == null
        ? other$targetIndex != null
        : !this$targetIndex.equals(other$targetIndex)) {
      return false;
    }
    final Object this$sourceIndexFilterQuery = sourceIndexFilterQuery;
    final Object other$sourceIndexFilterQuery = other.sourceIndexFilterQuery;
    if (this$sourceIndexFilterQuery == null
        ? other$sourceIndexFilterQuery != null
        : !this$sourceIndexFilterQuery.equals(other$sourceIndexFilterQuery)) {
      return false;
    }
    final Object this$mappingScript = mappingScript;
    final Object other$mappingScript = other.mappingScript;
    if (this$mappingScript == null
        ? other$mappingScript != null
        : !this$mappingScript.equals(other$mappingScript)) {
      return false;
    }
    return true;
  }

  public IndexMappingCreator getSourceIndex() {
    return sourceIndex;
  }

  public IndexMappingCreator getTargetIndex() {
    return targetIndex;
  }
}
