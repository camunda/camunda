/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.document;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import java.util.Map;
import java.util.concurrent.Callable;
import org.elasticsearch.index.query.QueryBuilder;

public class UpdateDataStep extends UpgradeStep {

  private final QueryBuilder query;
  private final String updateScript;
  private Map<String, Object> parameters;
  private final Callable<Map<String, Object>> paramMapProvider;

  public UpdateDataStep(
      final IndexMappingCreator index, final QueryBuilder query, final String updateScript) {
    this(index, query, updateScript, null, null);
  }

  public UpdateDataStep(
      final IndexMappingCreator index,
      final QueryBuilder query,
      final String updateScript,
      final Map<String, Object> parameters) {
    this(index, query, updateScript, parameters, null);
  }

  public UpdateDataStep(
      final IndexMappingCreator index,
      final QueryBuilder query,
      final String updateScript,
      final Map<String, Object> parameters,
      final Callable<Map<String, Object>> paramMapProvider) {
    super(index);
    this.query = query;
    this.updateScript = updateScript;
    this.parameters = parameters;
    this.paramMapProvider = paramMapProvider;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.DATA_UPDATE;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    if (paramMapProvider != null) {
      try {
        parameters = paramMapProvider.call();
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
    schemaUpgradeClient.updateDataByIndexName(index, query, updateScript, parameters);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof UpdateDataStep;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $query = query;
    result = result * PRIME + ($query == null ? 43 : $query.hashCode());
    final Object $updateScript = updateScript;
    result = result * PRIME + ($updateScript == null ? 43 : $updateScript.hashCode());
    final Object $parameters = parameters;
    result = result * PRIME + ($parameters == null ? 43 : $parameters.hashCode());
    final Object $paramMapProvider = paramMapProvider;
    result = result * PRIME + ($paramMapProvider == null ? 43 : $paramMapProvider.hashCode());
    return result;
  }

  @Override
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
    final Object this$query = query;
    final Object other$query = other.query;
    if (this$query == null ? other$query != null : !this$query.equals(other$query)) {
      return false;
    }
    final Object this$updateScript = updateScript;
    final Object other$updateScript = other.updateScript;
    if (this$updateScript == null
        ? other$updateScript != null
        : !this$updateScript.equals(other$updateScript)) {
      return false;
    }
    final Object this$parameters = parameters;
    final Object other$parameters = other.parameters;
    if (this$parameters == null
        ? other$parameters != null
        : !this$parameters.equals(other$parameters)) {
      return false;
    }
    final Object this$paramMapProvider = paramMapProvider;
    final Object other$paramMapProvider = other.paramMapProvider;
    if (this$paramMapProvider == null
        ? other$paramMapProvider != null
        : !this$paramMapProvider.equals(other$paramMapProvider)) {
      return false;
    }
    return true;
  }
}
