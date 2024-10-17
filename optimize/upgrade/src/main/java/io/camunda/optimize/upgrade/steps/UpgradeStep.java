/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps;

import com.google.common.annotations.VisibleForTesting;
import io.camunda.optimize.service.db.schema.IndexLookupUtil;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;

public abstract class UpgradeStep {

  protected IndexMappingCreator index;

  // This should always be false in real upgrades. In test scenarios, it can be set to true to avoid
  // failing conversion of test indices that don't have lookups
  @VisibleForTesting protected boolean skipIndexConversion = false;

  protected UpgradeStep(final IndexMappingCreator index) {
    this.index = index;
  }

  public UpgradeStep(IndexMappingCreator index, boolean skipIndexConversion) {
    this.index = index;
    this.skipIndexConversion = skipIndexConversion;
  }

  protected UpgradeStep() {}

  public abstract UpgradeStepType getType();

  protected abstract void performUpgradeStep(SchemaUpgradeClient<?, ?> schemaUpgradeClient);

  public void execute(final SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    if (!skipIndexConversion && index != null) {
      index = IndexLookupUtil.convertIndexForDatabase(index, schemaUpgradeClient.getDatabaseType());
    }
    performUpgradeStep(schemaUpgradeClient);
  }

  public IndexMappingCreator getIndex() {
    return this.index;
  }

  public boolean isSkipIndexConversion() {
    return this.skipIndexConversion;
  }

  public void setIndex(IndexMappingCreator index) {
    this.index = index;
  }

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
    final Object this$index = this.getIndex();
    final Object other$index = other.getIndex();
    if (this$index == null ? other$index != null : !this$index.equals(other$index)) {
      return false;
    }
    if (this.isSkipIndexConversion() != other.isSkipIndexConversion()) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UpgradeStep;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $index = this.getIndex();
    result = result * PRIME + ($index == null ? 43 : $index.hashCode());
    result = result * PRIME + (this.isSkipIndexConversion() ? 79 : 97);
    return result;
  }

  public String toString() {
    return "UpgradeStep(index="
        + this.getIndex()
        + ", skipIndexConversion="
        + this.isSkipIndexConversion()
        + ")";
  }

  public void setSkipIndexConversion(boolean skipIndexConversion) {
    this.skipIndexConversion = skipIndexConversion;
  }
}
