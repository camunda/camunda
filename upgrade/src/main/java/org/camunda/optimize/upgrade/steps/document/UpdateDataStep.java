/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.document;

import lombok.SneakyThrows;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.Map;
import java.util.concurrent.Callable;

public class UpdateDataStep implements UpgradeStep {
  private final String indexName;
  private final QueryBuilder query;
  private final String updateScript;
  private Map<String, Object> parameters;
  private final Callable<Map<String, Object>> paramMapProvider;

  public UpdateDataStep(String indexName, QueryBuilder query, String updateScript) {
    this(indexName, query, updateScript, null, null);
  }

  public UpdateDataStep(String indexName, QueryBuilder query, String updateScript,
                        final Callable<Map<String, Object>> paramMapProvider) {
    this(indexName, query, updateScript, null, paramMapProvider);
  }

  public UpdateDataStep(String indexName, QueryBuilder query, String updateScript,
                        Map<String, Object> parameters) {
    this(indexName, query, updateScript, parameters, null);
  }

  public UpdateDataStep(String indexName, QueryBuilder query, String updateScript, Map<String, Object> parameters,
                        final Callable<Map<String, Object>> paramMapProvider) {
    this.indexName = indexName;
    this.query = query;
    this.updateScript = updateScript;
    this.parameters = parameters;
    this.paramMapProvider = paramMapProvider;
  }

  @Override
  @SneakyThrows
  public void execute(SchemaUpgradeClient schemaUpgradeClient) {
    if (paramMapProvider != null) {
      parameters = paramMapProvider.call();
    }
    schemaUpgradeClient.updateDataByIndexName(indexName, query, updateScript, parameters);
  }
}
