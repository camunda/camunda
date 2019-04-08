/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.document;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.Map;


public class UpdateDataStep implements UpgradeStep {
  private final String typeName;
  private final QueryBuilder query;
  private final String updateScript;
  private final Map<String, Object> parameters;

  public UpdateDataStep(String typeName, QueryBuilder query, String updateScript) {
    this.typeName = typeName;
    this.query = query;
    this.updateScript = updateScript;
    this.parameters = null;
  }

  public UpdateDataStep(String typeName, QueryBuilder query, String updateScript, Map<String, Object> parameters) {
    this.typeName = typeName;
    this.query = query;
    this.updateScript = updateScript;
    this.parameters = parameters;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.updateDataByTypeName(typeName, query, updateScript, parameters);
  }

}
