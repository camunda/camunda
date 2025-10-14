/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Map;
import java.util.Set;

public class BatchOperation {

  private static final Map<String, Set<String>> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "export-items-on-creation",
          Set.of(
              "zeebe.broker.exporters.camundaexporter.args.batchOperation.exportItemsOnCreation"));

  private final String databaseName;

  /**
   * Export the batch operation items when the initial chunk records are processed. If set to <code>
   * false</code>, the batch operation items will be exported only when they have been processed and
   * are completed or failed.
   */
  private boolean exportItemsOnCreation = true;

  public BatchOperation(final String databaseName) {
    this.databaseName = databaseName;
  }

  public boolean isExportItemsOnCreation() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".export-items-on-creation",
        exportItemsOnCreation,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BROKER_PROPERTIES.get("export-items-on-creation"));
  }

  public void setExportItemsOnCreation(final boolean exportItemsOnCreation) {
    this.exportItemsOnCreation = exportItemsOnCreation;
  }

  private String prefix() {
    return "camunda.data.secondary-storage." + databaseName.toLowerCase() + ".batch-operations";
  }
}
