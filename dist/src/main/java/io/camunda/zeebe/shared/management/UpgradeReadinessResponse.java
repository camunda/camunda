/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.migration.MigrationConditionStatus;
import java.util.Map;

/**
 * Response body of the {@code upgradeReadiness} actuator endpoint.
 *
 * <p>Grouped by physical tenant first, condition name second — not "tenants" alone, since Camunda
 * already has an unrelated multi-tenancy concept (process-engine tenants on process instances); a
 * physical tenant is an independent engine inside one orchestration cluster (its own partition
 * group, its own secondary storage).
 *
 * @param upgradeable {@code true} only once every registered condition reports {@code MIGRATED} for
 *     every known physical tenant; {@code false} whenever nothing is known at all, since an empty
 *     response must never be mistaken for readiness
 * @param physicalTenants every known physical tenant's condition breakdown, keyed by physical
 *     tenant ID, then by {@code conditionName()}. A physical tenant only appears once at least one
 *     provider has reported it; a condition a provider does not report for a tenant it *does* know
 *     about (e.g. a lookup gap, not "does not apply") is filled in as {@code UNKNOWN} rather than
 *     omitted, so a gap can never be silently read as readiness.
 */
public record UpgradeReadinessResponse(
    boolean upgradeable, Map<String, Map<String, MigrationConditionStatus>> physicalTenants) {}
