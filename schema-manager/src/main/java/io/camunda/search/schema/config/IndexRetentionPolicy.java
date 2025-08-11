/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.config;

/**
 * Represents a retention policy for a specific index or set of indices. Contains the policy name
 * and minimum age for retention.
 *
 * <p>The `index` field can be:
 *
 * <ul>
 *   <li>Exact index names as defined in IndexDescriptor implementations including component name
 *       (e.g., "camunda-usage-metric-tu").
 *   <li>Patterns using wildcards to match multiple indices (e.g., "camunda-usage-metric.*" matches
 *       "camunda-usage-metric" and "camunda-usage-metric-tu").
 * </ul>
 */
public class IndexRetentionPolicy {

  private String index;
  private String policyName;
  private String minimumAge;

  public IndexRetentionPolicy() {
    // Default constructor for configuration binding
  }

  public IndexRetentionPolicy(
      final String index, final String policyName, final String minimumAge) {
    this.index = index;
    this.policyName = policyName;
    this.minimumAge = minimumAge;
  }

  public String getIndex() {
    return index;
  }

  public void setIndex(final String index) {
    this.index = index;
  }

  public String getPolicyName() {
    return policyName;
  }

  public void setPolicyName(final String policyName) {
    this.policyName = policyName;
  }

  public String getMinimumAge() {
    return minimumAge;
  }

  public void setMinimumAge(final String minimumAge) {
    this.minimumAge = minimumAge;
  }

  @Override
  public String toString() {
    return "IndexRetentionPolicy{"
        + "index='"
        + index
        + '\''
        + ", policyName='"
        + policyName
        + '\''
        + ", minimumAge='"
        + minimumAge
        + '\''
        + '}';
  }
}
