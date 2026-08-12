/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Reports whether a single upgrade-readiness condition is met, per physical tenant — e.g. "the
 * RDBMS schema is migrated to the current application version," or "every exporter has flushed all
 * previous-version records." Implementations are collected as Spring beans by the upgrade-readiness
 * endpoint (one bean per condition) and combined: the cluster is reported as upgradeable only once
 * every registered condition reports {@link MigrationState#MIGRATED} for every known physical
 * tenant.
 *
 * <p>A physical tenant is an independent engine inside one orchestration cluster — its own
 * partition group, its own secondary storage — so a condition can genuinely differ per tenant (one
 * tenant's schema migrated, another's still in progress). A missing entry for a tenant this
 * provider is not responsible for (e.g. a tenant on a different secondary storage type) is not an
 * error; the aggregator only requires agreement across the tenants a provider actually reports.
 *
 * <p>Some conditions are local to this node (e.g. schema-version checks against centralized, shared
 * secondary storage); others need to resolve state distributed across partitions and replicas.
 * Either way, {@link #getMigrationStatus()} backs a polled actuator endpoint and must be safe to
 * call frequently, and must never throw — implementations report {@link MigrationState#UNKNOWN}
 * instead of propagating an exception.
 */
@NullMarked
public interface MigrationStatusProvider {

  /**
   * @return the stable identifier for this condition, used as its key in the upgrade-readiness
   *     response (e.g. {@code "rdbmsSchemaMigrated"}). Must be stable across calls and unique
   *     across all registered providers.
   */
  String conditionName();

  /**
   * @return the current status of this condition, keyed by physical tenant ID. Must not throw;
   *     report {@link MigrationState#UNKNOWN} for a given tenant instead of omitting it whenever a
   *     failure is scoped to that tenant. Only omit a tenant entirely when this condition genuinely
   *     does not apply to it.
   */
  Map<String, MigrationConditionStatus> getMigrationStatus();
}
