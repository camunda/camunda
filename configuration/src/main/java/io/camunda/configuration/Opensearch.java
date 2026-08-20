/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class Opensearch extends DocumentBasedSecondaryStorageDatabase {

  private boolean awsEnabled = false;

  @Override
  public String databaseName() {
    return "opensearch";
  }

  public boolean isAwsEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        "camunda.data.secondary-storage.opensearch.aws-enabled",
        awsEnabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        Set.of(
            "camunda.database.awsEnabled",
            "camunda.tasklist.opensearch.awsEnabled",
            "camunda.operate.opensearch.awsEnabled",
            "zeebe.broker.exporters.camundaexporter.args.connect.awsEnabled"));
  }

  public void setAwsEnabled(final boolean awsEnabled) {
    this.awsEnabled = awsEnabled;
  }
}
