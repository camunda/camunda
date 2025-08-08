/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.config;

import java.util.HashMap;
import java.util.Map;

public class RetentionConfiguration {

  private static final String DEFAULT_RETENTION_MINIMUM_AGE = "30d";
  private static final String DEFAULT_RETENTION_POLICY_NAME = "camunda-history-retention-policy";
  private boolean enabled = false;
  private String minimumAge = DEFAULT_RETENTION_MINIMUM_AGE;
  private String policyName = DEFAULT_RETENTION_POLICY_NAME;

  // Map of index names or patterns to index-specific retention policies.
  // Keys can be:
  // - Exact index names as defined in IndexDescriptor implementations including component name
  //   (e.g., "camunda-usage-metric-tu")
  // - Patterns using wildcards to match multiple indices (e.g., "camunda-usage-metric.*" matches
  //   "camunda-usage-metric" and "camunda-usage-metric-tu")
  private Map<String, IndexRetentionPolicy> indexPolicies = new HashMap<>();

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

  public Map<String, IndexRetentionPolicy> getIndexPolicies() {
    return indexPolicies;
  }

  public void setIndexPolicies(final Map<String, IndexRetentionPolicy> indexPolicies) {
    this.indexPolicies = indexPolicies != null ? indexPolicies : new HashMap<>();
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
        + ", indexPolicies="
        + indexPolicies
        + '}';
  }
}
