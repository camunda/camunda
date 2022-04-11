/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.steps.document;

import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.Map;
import java.util.concurrent.Callable;

@EqualsAndHashCode(callSuper = true)
public class UpdateDataStep extends UpgradeStep {
  private final QueryBuilder query;
  private final String updateScript;
  private Map<String, Object> parameters;
  private final Callable<Map<String, Object>> paramMapProvider;

  public UpdateDataStep(final IndexMappingCreator index, final QueryBuilder query, final String updateScript) {
    this(index, query, updateScript, null, null);
  }

  public UpdateDataStep(final IndexMappingCreator index, final QueryBuilder query, final String updateScript,
                        final Map<String, Object> parameters) {
    this(index, query, updateScript, parameters, null);
  }

  public UpdateDataStep(final IndexMappingCreator index, final QueryBuilder query, final String updateScript,
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
  @SneakyThrows
  public void execute(SchemaUpgradeClient schemaUpgradeClient) {
    if (paramMapProvider != null) {
      parameters = paramMapProvider.call();
    }
    schemaUpgradeClient.updateDataByIndexName(index, query, updateScript, parameters);
  }
}
