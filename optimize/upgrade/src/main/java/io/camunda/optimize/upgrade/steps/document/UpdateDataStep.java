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
  public void performUpgradeStep(SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    if (paramMapProvider != null) {
      try {
        parameters = paramMapProvider.call();
      } catch (Exception e) {
        throw new OptimizeRuntimeException(e);
      }
    }
    schemaUpgradeClient.updateDataByIndexName(index, queryWrapper, updateScript, parameters);
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UpdateDataStep)) {
      return false;
    }
    final UpdateDataStep other = (UpdateDataStep) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$queryWrapper = this.queryWrapper;
    final Object other$queryWrapper = other.queryWrapper;
    if (this$queryWrapper == null
        ? other$queryWrapper != null
        : !this$queryWrapper.equals(other$queryWrapper)) {
      return false;
    }
    final Object this$updateScript = this.updateScript;
    final Object other$updateScript = other.updateScript;
    if (this$updateScript == null
        ? other$updateScript != null
        : !this$updateScript.equals(other$updateScript)) {
      return false;
    }
    final Object this$parameters = this.parameters;
    final Object other$parameters = other.parameters;
    if (this$parameters == null
        ? other$parameters != null
        : !this$parameters.equals(other$parameters)) {
      return false;
    }
    final Object this$paramMapProvider = this.paramMapProvider;
    final Object other$paramMapProvider = other.paramMapProvider;
    if (this$paramMapProvider == null
        ? other$paramMapProvider != null
        : !this$paramMapProvider.equals(other$paramMapProvider)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UpdateDataStep;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $queryWrapper = this.queryWrapper;
    result = result * PRIME + ($queryWrapper == null ? 43 : $queryWrapper.hashCode());
    final Object $updateScript = this.updateScript;
    result = result * PRIME + ($updateScript == null ? 43 : $updateScript.hashCode());
    final Object $parameters = this.parameters;
    result = result * PRIME + ($parameters == null ? 43 : $parameters.hashCode());
    final Object $paramMapProvider = this.paramMapProvider;
    result = result * PRIME + ($paramMapProvider == null ? 43 : $paramMapProvider.hashCode());
    return result;
  }
}
