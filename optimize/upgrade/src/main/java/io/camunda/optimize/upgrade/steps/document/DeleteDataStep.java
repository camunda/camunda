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
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import java.util.Objects;

public class DeleteDataStep extends UpgradeStep {

  private final DatabaseQueryWrapper queryWrapper;

  public DeleteDataStep(final IndexMappingCreator index, final DatabaseQueryWrapper queryWrapper) {
    super(index);
    this.queryWrapper = queryWrapper;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.DATA_DELETE;
  }

  @Override
  public void performUpgradeStep(final SchemaUpgradeClient<?, ?, ?> schemaUpgradeClient) {
    schemaUpgradeClient.deleteDataByIndexName(index, queryWrapper);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DeleteDataStep;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final DeleteDataStep that = (DeleteDataStep) o;
    return Objects.equals(queryWrapper, that.queryWrapper);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), queryWrapper);
  }
}
