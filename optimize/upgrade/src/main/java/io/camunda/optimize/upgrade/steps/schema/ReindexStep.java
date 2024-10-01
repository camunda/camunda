/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.schema;

import io.camunda.optimize.service.db.DatabaseQueryWrapper;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;

public class ReindexStep extends UpgradeStep {

  private final IndexMappingCreator sourceIndex;
  private final IndexMappingCreator targetIndex;
  private final DatabaseQueryWrapper queryWrapper;
  private final String mappingScript;

  public ReindexStep(
      final IndexMappingCreator sourceIndex,
      final IndexMappingCreator targetIndex,
      final DatabaseQueryWrapper queryWrapper) {
    this(sourceIndex, targetIndex, queryWrapper, null);
  }

  public ReindexStep(
      IndexMappingCreator sourceIndex,
      IndexMappingCreator targetIndex,
      DatabaseQueryWrapper queryWrapper,
      String mappingScript) {
    this.sourceIndex = sourceIndex;
    this.targetIndex = targetIndex;
    this.queryWrapper = queryWrapper;
    this.mappingScript = mappingScript;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.REINDEX;
  }

  @Override
  public void performUpgradeStep(final SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    schemaUpgradeClient.reindex(sourceIndex, targetIndex, queryWrapper, mappingScript);
  }

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
    final Object this$sourceIndex = this.sourceIndex;
    final Object other$sourceIndex = other.sourceIndex;
    if (this$sourceIndex == null
        ? other$sourceIndex != null
        : !this$sourceIndex.equals(other$sourceIndex)) {
      return false;
    }
    final Object this$targetIndex = this.targetIndex;
    final Object other$targetIndex = other.targetIndex;
    if (this$targetIndex == null
        ? other$targetIndex != null
        : !this$targetIndex.equals(other$targetIndex)) {
      return false;
    }
    final Object this$queryWrapper = this.queryWrapper;
    final Object other$queryWrapper = other.queryWrapper;
    if (this$queryWrapper == null
        ? other$queryWrapper != null
        : !this$queryWrapper.equals(other$queryWrapper)) {
      return false;
    }
    final Object this$mappingScript = this.mappingScript;
    final Object other$mappingScript = other.mappingScript;
    if (this$mappingScript == null
        ? other$mappingScript != null
        : !this$mappingScript.equals(other$mappingScript)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReindexStep;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $sourceIndex = this.sourceIndex;
    result = result * PRIME + ($sourceIndex == null ? 43 : $sourceIndex.hashCode());
    final Object $targetIndex = this.targetIndex;
    result = result * PRIME + ($targetIndex == null ? 43 : $targetIndex.hashCode());
    final Object $queryWrapper = this.queryWrapper;
    result = result * PRIME + ($queryWrapper == null ? 43 : $queryWrapper.hashCode());
    final Object $mappingScript = this.mappingScript;
    result = result * PRIME + ($mappingScript == null ? 43 : $mappingScript.hashCode());
    return result;
  }

  public IndexMappingCreator getSourceIndex() {
    return this.sourceIndex;
  }

  public IndexMappingCreator getTargetIndex() {
    return this.targetIndex;
  }
}
