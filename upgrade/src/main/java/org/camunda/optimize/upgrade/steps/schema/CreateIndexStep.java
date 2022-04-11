/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import lombok.EqualsAndHashCode;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
public class CreateIndexStep extends UpgradeStep {
  private Set<String> readOnlyAliases = new HashSet<>();

  public CreateIndexStep(final IndexMappingCreator index) {
    super(index);
  }

  public CreateIndexStep(final IndexMappingCreator index, final Set<String> readOnlyAliases) {
    super(index);
    this.readOnlyAliases = readOnlyAliases;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.SCHEMA_CREATE_INDEX;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.createOrUpdateIndex(index, readOnlyAliases);
  }
}
