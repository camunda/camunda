/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.steps.document;

import lombok.EqualsAndHashCode;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;

@EqualsAndHashCode(callSuper = true)
public class InsertDataStep extends UpgradeStep {
  private final String data;

  public InsertDataStep(final IndexMappingCreator index, final String data) {
    super(index);
    this.data = data;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.DATA_INSERT;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.insertDataByIndexName(index, data);
  }

}
