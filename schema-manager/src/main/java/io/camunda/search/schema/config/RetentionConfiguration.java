/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.config;

import java.time.Duration;

public class RetentionConfiguration {

  private static final String DEFAULT_RETENTION_MINIMUM_AGE = "30d";
  private static final String DEFAULT_RETENTION_POLICY_NAME = "camunda-retention-policy";
  private static final String DEFAULT_USAGE_METRICS_MINIMUM_AGE = "730d"; // 2 years
  private static final String DEFAULT_USAGE_METRICS_POLICY_NAME =
      "camunda-usage-metrics-retention-policy";
  private static final Duration DEFAULT_APPLY_POLICY_JOB_INTERVAL = Duration.ofHours(1);
  private boolean enabled = false;
  private String minimumAge = DEFAULT_RETENTION_MINIMUM_AGE;
  private String policyName = DEFAULT_RETENTION_POLICY_NAME;
  private String usageMetricsMinimumAge = DEFAULT_USAGE_METRICS_MINIMUM_AGE;
  private String usageMetricsPolicyName = DEFAULT_USAGE_METRICS_POLICY_NAME;
  private String jobMetricsBatchMinimumAge = DEFAULT_RETENTION_MINIMUM_AGE;
  private String jobMetricsBatchPolicyName = DEFAULT_RETENTION_POLICY_NAME;
  private Duration applyPolicyJobInterval = DEFAULT_APPLY_POLICY_JOB_INTERVAL;

  public String getJobMetricsBatchMinimumAge() {
    return jobMetricsBatchMinimumAge;
  }

  public void setJobMetricsBatchMinimumAge(final String jobMetricsBatchMinimumAge) {
    this.jobMetricsBatchMinimumAge = jobMetricsBatchMinimumAge;
  }

  public String getJobMetricsBatchPolicyName() {
    return jobMetricsBatchPolicyName;
  }

  public void setJobMetricsBatchPolicyName(final String jobMetricsBatchPolicyName) {
    this.jobMetricsBatchPolicyName = jobMetricsBatchPolicyName;
  }

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

  public String getUsageMetricsMinimumAge() {
    return usageMetricsMinimumAge;
  }

  public void setUsageMetricsMinimumAge(final String usageMetricsMinimumAge) {
    this.usageMetricsMinimumAge = usageMetricsMinimumAge;
  }

  public String getUsageMetricsPolicyName() {
    return usageMetricsPolicyName;
  }

  public void setUsageMetricsPolicyName(final String usageMetricsPolicyName) {
    this.usageMetricsPolicyName = usageMetricsPolicyName;
  }

  public Duration getApplyPolicyJobInterval() {
    return applyPolicyJobInterval;
  }

  public void setApplyPolicyJobInterval(final Duration applyPolicyJobInterval) {
    this.applyPolicyJobInterval = applyPolicyJobInterval;
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
        + ", usageMetricsMinimumAge='"
        + usageMetricsMinimumAge
        + '\''
        + ", usageMetricsPolicyName='"
        + usageMetricsPolicyName
        + '\''
        + ", applyPolicyJobInterval="
        + applyPolicyJobInterval
        + '}';
  }
}
