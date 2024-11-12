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
  public void performUpgradeStep(final SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    schemaUpgradeClient.reindex(sourceIndex, targetIndex, queryWrapper, mappingScript);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ReindexStep;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  public IndexMappingCreator getSourceIndex() {
    return sourceIndex;
  }

  public IndexMappingCreator getTargetIndex() {
    return targetIndex;
  }
}
