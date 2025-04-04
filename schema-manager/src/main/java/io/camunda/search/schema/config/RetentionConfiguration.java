/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.config;

public class RetentionConfiguration {

  private static final String DEFAULT_RETENTION_MINIMUM_AGE = "30d";
  private static final String DEFAULT_RETENTION_POLICY_NAME = "camunda-history-retention-policy";
  private boolean enabled = false;
  private String minimumAge = DEFAULT_RETENTION_MINIMUM_AGE;
  private String policyName = DEFAULT_RETENTION_POLICY_NAME;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getMinimumAge() {
    return minimumAge;
  }

  public void setMinimumAge(final String minimumAge) {
    this.minimumAge = minimumAge;
  }

  public String getPolicyName() {
    return policyName;
  }

  public void setPolicyName(final String policyName) {
    this.policyName = policyName;
  }

  @Override
  public String toString() {
    return "RetentionConfiguration{"
        + "enabled="
        + enabled
        + ", minimumAge='"
        + minimumAge
        + '\''
        + ", policyName='"
        + policyName
        + '\''
        + '}';
  }
}
