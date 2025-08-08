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
 */
public class IndexRetentionPolicy {

  private String policyName;
  private String minimumAge;

  public IndexRetentionPolicy() {
    // Default constructor for configuration binding
  }

  public IndexRetentionPolicy(final String policyName, final String minimumAge) {
    this.policyName = policyName;
    this.minimumAge = minimumAge;
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
        + "policyName='"
        + policyName
        + '\''
        + ", minimumAge='"
        + minimumAge
        + '\''
        + '}';
  }
}
