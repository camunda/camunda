/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.document;

import io.camunda.optimize.service.db.DatabaseQueryWrapper;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import java.util.Map;
import java.util.concurrent.Callable;

public class UpdateDataStep extends UpgradeStep {

  private final DatabaseQueryWrapper queryWrapper;
  private final String updateScript;
  private Map<String, Object> parameters;
  private final Callable<Map<String, Object>> paramMapProvider;

  public UpdateDataStep(
      final IndexMappingCreator index,
      final DatabaseQueryWrapper queryWrapper,
      final String updateScript) {
    this(index, queryWrapper, updateScript, null, null);
  }

  public UpdateDataStep(
      final IndexMappingCreator index,
      final DatabaseQueryWrapper queryWrapper,
      final String updateScript,
      final Map<String, Object> parameters) {
    this(index, queryWrapper, updateScript, parameters, null);
  }

  public UpdateDataStep(
      final IndexMappingCreator index,
      final DatabaseQueryWrapper queryWrapper,
      final String updateScript,
      final Map<String, Object> parameters,
      final Callable<Map<String, Object>> paramMapProvider) {
    super(index);
    this.queryWrapper = queryWrapper;
    this.updateScript = updateScript;
    this.parameters = parameters;
    this.paramMapProvider = paramMapProvider;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.DATA_UPDATE;
  }

  @Override
  public void performUpgradeStep(final SchemaUpgradeClient<?, ?, ?> schemaUpgradeClient) {
    if (paramMapProvider != null) {
      try {
        parameters = paramMapProvider.call();
      } catch (final Exception e) {
        throw new OptimizeRuntimeException(e);
      }
    }
    schemaUpgradeClient.updateDataByIndexName(index, queryWrapper, updateScript, parameters);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof UpdateDataStep;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }
}
