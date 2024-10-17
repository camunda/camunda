/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.schema;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;

public class UpdateMappingIndexStep extends UpgradeStep {

  public UpdateMappingIndexStep(final IndexMappingCreator index) {
    super(index);
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.SCHEMA_UPDATE_MAPPING;
  }

  @Override
  public void performUpgradeStep(final SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    schemaUpgradeClient.updateIndexDynamicSettingsAndMappings(index);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof UpdateMappingIndexStep;
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
