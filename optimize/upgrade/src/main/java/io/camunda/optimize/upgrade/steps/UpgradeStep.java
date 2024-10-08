/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps;

import com.google.common.annotations.VisibleForTesting;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.db.index.IndexLookupUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public abstract class UpgradeStep {
  protected IndexMappingCreator index;

  // This should always be false in real upgrades. In test scenarios, it can be set to true to avoid
  // failing conversion of test indices that don't have lookups
  @VisibleForTesting @Setter protected boolean skipIndexConversion = false;

  protected UpgradeStep(final IndexMappingCreator index) {
    this.index = index;
  }

  public abstract UpgradeStepType getType();

  protected abstract void performUpgradeStep(SchemaUpgradeClient<?, ?> schemaUpgradeClient);

  public void execute(final SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    if (!skipIndexConversion && index != null) {
      index = IndexLookupUtil.convertIndexForDatabase(index, schemaUpgradeClient.getDatabaseType());
    }
    performUpgradeStep(schemaUpgradeClient);
  }
}
