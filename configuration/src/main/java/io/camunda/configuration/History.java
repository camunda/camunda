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

public class History {

  private static final boolean DEFAULT_HISTORY_PROCESS_INSTANCE_ENABLED = true;

  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "process-instance-enabled",
          "zeebe.broker.exporters.camundaexporter.args.history.process-instance-enabled");

  private final String prefix;

  private boolean processInstanceEnabled = DEFAULT_HISTORY_PROCESS_INSTANCE_ENABLED;

  public History(final String databaseName) {
    prefix = "camunda.data.secondary-storage.%s.history".formatted(databaseName);
  }

  public boolean isProcessInstanceEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".process-instance-enabled",
        processInstanceEnabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        Set.of(LEGACY_BROKER_PROPERTIES.get("process-instance-enabled")));
  }

  public void setProcessInstanceEnabled(final boolean processInstanceEnabled) {
    this.processInstanceEnabled = processInstanceEnabled;
  }
}
