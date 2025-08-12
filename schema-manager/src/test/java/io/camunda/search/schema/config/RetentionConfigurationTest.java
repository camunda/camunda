/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RetentionConfigurationTest {

  @Test
  void shouldThrowExceptionWhenDuplicatePolicyNamesInRetentionConfiguration() {
    // given
    final RetentionConfiguration retention = new RetentionConfiguration();

    final List<IndexRetentionPolicy> duplicatePolicies =
        List.of(
            new IndexRetentionPolicy("user_policy", "7d", List.of("user-activity")),
            new IndexRetentionPolicy("user_policy", "14d", List.of("user-logs")),
            new IndexRetentionPolicy("admin_policy", "30d", List.of("admin-logs")),
            new IndexRetentionPolicy("user_policy", "7d", List.of("user-events")));

    // when & then
    assertThatThrownBy(() -> retention.setIndexPolicies(duplicatePolicies))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate policy names found in retention policies configuration.")
        .hasMessageContaining("Consider using a single policy with multiple indices instead.")
        .hasMessageContaining("Duplicate policy details:")
        .hasMessageContaining("Policy name 'user_policy' appears 3 times")
        .hasMessageContaining("minimumAge: '7d', indices: [user-activity]")
        .hasMessageContaining("minimumAge: '14d', indices: [user-logs]")
        .hasMessageContaining("minimumAge: '7d', indices: [user-events]");
  }

  @Test
  void shouldAcceptUniqueValidIndexRetentionPolicies() {
    // given
    final RetentionConfiguration retention = new RetentionConfiguration();

    final List<IndexRetentionPolicy> validPolicies =
        List.of(
            new IndexRetentionPolicy("user_policy", "7d", List.of("user-activity", "user-logs")),
            new IndexRetentionPolicy("admin_policy", "30d", List.of("admin-logs")),
            new IndexRetentionPolicy("system_policy", "90d", List.of("system-events")));

    // when & then
    assertThatNoException().isThrownBy(() -> retention.setIndexPolicies(validPolicies));
    assertThat(retention.getIndexPolicies()).hasSize(3);
    assertThat(retention.getIndexPolicies().get(0).getPolicyName()).isEqualTo("user_policy");
    assertThat(retention.getIndexPolicies().get(1).getPolicyName()).isEqualTo("admin_policy");
    assertThat(retention.getIndexPolicies().get(2).getPolicyName()).isEqualTo("system_policy");
  }
}
