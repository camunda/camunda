/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.document;

import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;


public class InsertDataStep implements UpgradeStep {
  private final String data;
  private final IndexMappingCreator index;

  public InsertDataStep(final IndexMappingCreator index, final String data) {
    this.index = index;
    this.data = data;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.insertDataByIndexName(index, data);
  }

}
