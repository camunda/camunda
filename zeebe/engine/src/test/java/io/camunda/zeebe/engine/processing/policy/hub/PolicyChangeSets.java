/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.fanout.protocol.model.PolicyChangeSetKind;
import io.camunda.fanout.protocol.model.PolicyChangeSetV1;
import io.camunda.fanout.protocol.model.PolicyDeletions;
import io.camunda.fanout.protocol.model.PolicyTenant;
import io.camunda.fanout.protocol.model.PolicyUpserts;
import java.util.List;
import java.util.UUID;

/** Builds policy change sets for tests, with a unique policy ID each. */
final class PolicyChangeSets {

  private PolicyChangeSets() {}

  static PolicyChangeSetV1 delta(final PolicyUpserts upserts, final PolicyDeletions deletions) {
    return changeSet(PolicyChangeSetKind.DELTA, upserts, deletions);
  }

  static PolicyChangeSetV1 changeSet(
      final PolicyChangeSetKind kind,
      final PolicyUpserts upserts,
      final PolicyDeletions deletions) {
    return new PolicyChangeSetV1(
        "policy-" + UUID.randomUUID(), "Policy", null, 3L, 4L, kind, upserts, deletions);
  }

  /** A delta of the given policy from one version to the next. */
  static PolicyChangeSetV1 delta(
      final String policyId, final long policyVersion, final PolicyUpserts upserts) {
    return new PolicyChangeSetV1(
        policyId,
        "Policy",
        null,
        policyVersion - 1,
        policyVersion,
        PolicyChangeSetKind.DELTA,
        upserts,
        noDeletions());
  }

  /** Upserts of the given tenants. */
  static PolicyUpserts tenants(final PolicyTenant... tenants) {
    return new PolicyUpserts(
        List.of(tenants), List.of(), List.of(), List.of(), List.of(), List.of());
  }

  static PolicyUpserts noUpserts() {
    return new PolicyUpserts(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }

  static PolicyDeletions noDeletions() {
    return new PolicyDeletions(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }
}
