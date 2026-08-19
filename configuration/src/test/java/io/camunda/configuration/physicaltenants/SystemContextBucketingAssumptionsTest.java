/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

/**
 * Drift guard between the deny-list in {@link PhysicalTenantOverridePolicyValidation} and how
 * {@code SystemContext.validateConfiguration()} in {@code zeebe/broker} buckets its checks into a
 * root-only pass and a per-physical-tenant pass.
 *
 * <p>{@code SystemContext} cannot depend on the {@code configuration} module, so it cannot call
 * {@link PhysicalTenantOverridePolicyValidation#isNonOverridable} directly and re-derives the same
 * bucketing by hand (see the reproduction guide for #60513). This test asserts, through the real
 * policy, that a representative sample of the properties {@code SystemContext} treats as root-only
 * are actually non-overridable, and a representative sample of the properties it validates
 * per-tenant are actually overridable. If either assumption drifts — because the deny-list changed
 * without updating {@code SystemContext}, or vice versa — this test fails and forces a deliberate
 * reconciliation.
 */
class SystemContextBucketingAssumptionsTest {

  /**
   * Properties {@code SystemContext} validates exactly once, against the root {@code BrokerCfg},
   * because a per-tenant override is denied.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        // validateDiskConfig
        "data.primary-storage.disk.enable-monitoring",
        "data.primary-storage.disk.free-space.processing",
        // validateMaxAppendBatchSize has no direct camunda.* property (legacy
        // zeebe.broker.experimental.maxAppendBatchSize only), but the fixed-partitioning scheme
        // and replication factor it shares the root pass with are both denied:
        "cluster.partitioning.scheme",
        "cluster.replication-factor",
        "cluster.raft.enable-priority-election",
      })
  void shouldTreatRootOnlyPropertiesAsNonOverridable(final String relativeKey) {
    assertThat(PhysicalTenantOverridePolicyValidation.isNonOverridable(name(relativeKey)))
        .as(
            "SystemContext validates '%s' against the root BrokerCfg only; it must be "
                + "non-overridable per physical tenant, otherwise a tenant override would "
                + "silently bypass validation",
            relativeKey)
        .isTrue();
  }

  /**
   * Properties {@code SystemContext} validates once per physical tenant, against that tenant's own
   * {@code BrokerCfg}, because a per-tenant override is allowed.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        // validatePerTenantDataConfig
        "data.snapshot-period",
        "data.primary-storage.backup.required",
        "data.primary-storage.backup.store",
        // validatePerTenantExperimentalConfigs
        "cluster.partition-count",
        "cluster.global-listeners.user-task",
        "processing.engine.batch-operations.chunk-size",
      })
  void shouldTreatPerTenantPropertiesAsOverridable(final String relativeKey) {
    assertThat(PhysicalTenantOverridePolicyValidation.isNonOverridable(name(relativeKey)))
        .as(
            "SystemContext validates '%s' once per physical tenant, against that tenant's own "
                + "BrokerCfg; it must be overridable, otherwise validating it per-tenant instead "
                + "of once at the root would be pointless",
            relativeKey)
        .isFalse();
  }

  private static ConfigurationPropertyName name(final String relativeKey) {
    return ConfigurationPropertyName.of(relativeKey);
  }
}
