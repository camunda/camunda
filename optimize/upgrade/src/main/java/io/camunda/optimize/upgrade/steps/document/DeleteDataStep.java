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
import org.elasticsearch.index.query.QueryBuilder;

public class DeleteDataStep extends UpgradeStep {

  private final QueryBuilder query;

  public DeleteDataStep(final IndexMappingCreator index, final QueryBuilder query) {
    super(index);
    this.query = query;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.DATA_DELETE;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.deleteDataByIndexName(index, query);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DeleteDataStep;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $query = query;
    result = result * PRIME + ($query == null ? 43 : $query.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DeleteDataStep)) {
      return false;
    }
    final DeleteDataStep other = (DeleteDataStep) o;
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
    return true;
  }
}
