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

  public UpgradeStep(final IndexMappingCreator index, final boolean skipIndexConversion) {
    this.index = index;
    this.skipIndexConversion = skipIndexConversion;
  }

  protected UpgradeStep() {}

  public abstract UpgradeStepType getType();

  protected abstract void performUpgradeStep(SchemaUpgradeClient<?, ?, ?> schemaUpgradeClient);

  public void execute(final SchemaUpgradeClient<?, ?, ?> schemaUpgradeClient) {
    if (!skipIndexConversion && index != null) {
      index = IndexLookupUtil.convertIndexForDatabase(index, schemaUpgradeClient.getDatabaseType());
    }
    performUpgradeStep(schemaUpgradeClient);
  }

  public IndexMappingCreator getIndex() {
    return index;
  }

  public void setIndex(final IndexMappingCreator index) {
    this.index = index;
  }

  public boolean isSkipIndexConversion() {
    return skipIndexConversion;
  }

  public void setSkipIndexConversion(final boolean skipIndexConversion) {
    this.skipIndexConversion = skipIndexConversion;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UpgradeStep;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "UpgradeStep(index="
        + getIndex()
        + ", skipIndexConversion="
        + isSkipIndexConversion()
        + ")";
  }
}
