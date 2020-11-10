/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.document;

import lombok.AllArgsConstructor;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.index.query.QueryBuilder;

@AllArgsConstructor
public class DeleteDataStep implements UpgradeStep {
  private final String indexName;
  private final QueryBuilder query;

  @Override
  public void execute(SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.deleteDataByIndexName(indexName, query);
  }

}
