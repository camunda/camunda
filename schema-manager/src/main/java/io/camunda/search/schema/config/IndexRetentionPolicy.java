/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a retention policy that can be applied to multiple indices. Contains the policy name,
 * minimum age for retention, and a list of indices this policy applies to.
 *
 * <p>The `indices` field can contain:
 *
 * <ul>
 *   <li>Exact index names as defined in IndexDescriptor implementations including component name
 *       (e.g., "camunda-usage-metric-tu").
 *   <li>Patterns using wildcards to match multiple indices (e.g., "camunda-usage-metric.*" matches
 *       "camunda-usage-metric" and "camunda-usage-metric-tu").
 * </ul>
 */
public class IndexRetentionPolicy {

  private String policyName;
  private String minimumAge;
  private List<String> indices = new ArrayList<>();

  public IndexRetentionPolicy() {
    // Default constructor for configuration binding
  }

  public IndexRetentionPolicy(
      final String policyName, final String minimumAge, final List<String> indices) {
    this.policyName = policyName;
    this.minimumAge = minimumAge;
    this.indices = indices != null ? new ArrayList<>(indices) : new ArrayList<>();
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

  public List<String> getIndices() {
    return indices;
  }

  public void setIndices(final List<String> indices) {
    this.indices = indices != null ? new ArrayList<>(indices) : new ArrayList<>();
  }

  @Override
  public String toString() {
    return "IndexRetentionPolicy{"
        + "policyName='"
        + policyName
        + '\''
        + ", minimumAge='"
        + minimumAge
        + '\''
        + ", indices="
        + indices
        + '}';
  }
}
