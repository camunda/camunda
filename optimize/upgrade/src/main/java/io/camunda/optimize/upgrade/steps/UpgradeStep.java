/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClient;

public abstract class UpgradeStep {

  protected IndexMappingCreator index;

  public UpgradeStep(final IndexMappingCreator index) {
    this.index = index;
  }

  protected UpgradeStep() {}

  public abstract UpgradeStepType getType();

  public abstract void execute(SchemaUpgradeClient schemaUpgradeClient);

  public IndexMappingCreator getIndex() {
    return index;
  }

  public void setIndex(final IndexMappingCreator index) {
    this.index = index;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UpgradeStep;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $index = getIndex();
    result = result * PRIME + ($index == null ? 43 : $index.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UpgradeStep)) {
      return false;
    }
    final UpgradeStep other = (UpgradeStep) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$index = getIndex();
    final Object other$index = other.getIndex();
    if (this$index == null ? other$index != null : !this$index.equals(other$index)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UpgradeStep(index=" + getIndex() + ")";
  }
}
