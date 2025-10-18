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

public class DocumentBasedHistory {

  private static final boolean DEFAULT_HISTORY_PROCESS_INSTANCE_ENABLED = true;
  private static final String DEFAULT_HISTORY_POLICY_NAME = "camunda-retention-policy";

  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "process-instance-enabled",
          "zeebe.broker.exporters.camundaexporter.args.history.process-instance-enabled");

  private final String prefix;

  private boolean processInstanceEnabled = DEFAULT_HISTORY_PROCESS_INSTANCE_ENABLED;

  /** Defines the name of the created and applied ILM policy. */
  private String policyName = DEFAULT_HISTORY_POLICY_NAME;

  public DocumentBasedHistory(final String databaseName) {
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

  public String getPolicyName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".policy-name",
        policyName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyPolicyNameProperties());
  }

  public void setPolicyName(final String policyName) {
    this.policyName = policyName;
  }

  private Set<String> legacyPolicyNameProperties() {
    return Set.of(
        "camunda.database.retention.policyName",
        "zeebe.broker.exporters.camundaexporter.args.history.retention.policyName");
  }
}
