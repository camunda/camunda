/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.schema;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import java.util.HashSet;
import java.util.Set;

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

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof CreateIndexStep;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $readOnlyAliases = readOnlyAliases;
    result = result * PRIME + ($readOnlyAliases == null ? 43 : $readOnlyAliases.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CreateIndexStep)) {
      return false;
    }
    final CreateIndexStep other = (CreateIndexStep) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$readOnlyAliases = readOnlyAliases;
    final Object other$readOnlyAliases = other.readOnlyAliases;
    if (this$readOnlyAliases == null
        ? other$readOnlyAliases != null
        : !this$readOnlyAliases.equals(other$readOnlyAliases)) {
      return false;
    }
    return true;
  }
}
