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
import java.util.Objects;

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
      final IndexMappingCreator sourceIndex,
      final IndexMappingCreator targetIndex,
      final DatabaseQueryWrapper queryWrapper,
      final String mappingScript) {
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
  public void performUpgradeStep(final SchemaUpgradeClient<?, ?, ?> schemaUpgradeClient) {
    schemaUpgradeClient.reindex(sourceIndex, targetIndex, queryWrapper, mappingScript);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ReindexStep;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ReindexStep that = (ReindexStep) o;
    return Objects.equals(sourceIndex, that.sourceIndex)
        && Objects.equals(targetIndex, that.targetIndex)
        && Objects.equals(queryWrapper, that.queryWrapper)
        && Objects.equals(mappingScript, that.mappingScript);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sourceIndex, targetIndex, queryWrapper, mappingScript);
  }

  public IndexMappingCreator getSourceIndex() {
    return sourceIndex;
  }

  public IndexMappingCreator getTargetIndex() {
    return targetIndex;
  }
}
