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
import java.util.Map;
import java.util.stream.Collectors;

public class RetentionConfiguration {

  private static final String DEFAULT_RETENTION_MINIMUM_AGE = "30d";
  private static final String DEFAULT_RETENTION_POLICY_NAME = "camunda-history-retention-policy";
  private boolean enabled = false;
  private String minimumAge = DEFAULT_RETENTION_MINIMUM_AGE;
  private String policyName = DEFAULT_RETENTION_POLICY_NAME;

  private List<IndexRetentionPolicy> indexPolicies = new ArrayList<>();

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

  public List<IndexRetentionPolicy> getIndexPolicies() {
    return indexPolicies;
  }

  public void setIndexPolicies(final List<IndexRetentionPolicy> indexPolicies) {
    final List<IndexRetentionPolicy> policies =
        indexPolicies != null ? indexPolicies : new ArrayList<>();
    validateNoDuplicatePolicyNames(policies);
    this.indexPolicies = policies;
  }

  /**
   * Validates that there are no duplicate policy names in the provided list of index retention
   * policies.
   *
   * @param policies the list of index retention policies to validate
   * @throws IllegalArgumentException if duplicate policy names are found
   */
  private void validateNoDuplicatePolicyNames(final List<IndexRetentionPolicy> policies) {
    final Map<String, List<IndexRetentionPolicy>> policiesByName =
        policies.stream()
            .filter(policy -> policy.getPolicyName() != null)
            .collect(Collectors.groupingBy(IndexRetentionPolicy::getPolicyName));

    final List<String> duplicatePolicyNames =
        policiesByName.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .toList();

    if (!duplicatePolicyNames.isEmpty()) {
      final StringBuilder errorMessage = new StringBuilder();
      errorMessage.append("Duplicate policy names found in retention policies configuration. ");
      errorMessage.append("Each policy name must be unique.\n");
      errorMessage.append("Consider using a single policy with multiple indices instead.\n\n");
      errorMessage.append("Duplicate policy details:\n");

      for (final String duplicatePolicyName : duplicatePolicyNames) {
        final List<IndexRetentionPolicy> duplicates = policiesByName.get(duplicatePolicyName);
        errorMessage.append(
            String.format(
                "Policy name '%s' appears %d times:\n", duplicatePolicyName, duplicates.size()));

        for (int i = 0; i < duplicates.size(); i++) {
          final IndexRetentionPolicy policy = duplicates.get(i);
          errorMessage.append(
              String.format(
                  "  %d. minimumAge: '%s', indices: %s\n",
                  i + 1, policy.getMinimumAge(), policy.getIndices()));
        }
      }

      throw new IllegalArgumentException(errorMessage.toString());
    }
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
