/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import java.util.List;

/**
 * SPIKE (ADR-0036): Optimize's {@link MembershipPort}. CSL calls this lazily to resolve a
 * principal's mapping rules, groups, roles, and tenants from the OIDC token claims.
 *
 * <p>STUB: this spike returns the groups from the OIDC {@code groups} claim only and leaves roles,
 * tenants, and mapping rules empty, which matches Optimize's current model (Optimize authorizes on
 * its own collection/report permissions, not on engine RBAC). Mirror OC's {@code
 * NoDBMembershipService} shape: use CSL's {@code OidcGroupsExtractor} with the configured
 * groups-claim to pull groups directly from {@link MembershipQuery#tokenClaims()}.
 *
 * <p>Implementation task (see SPIKE-NOTES.md): confirm which memberships Optimize authorization
 * actually consumes and wire the real extraction (groups claim at minimum).
 */
public final class OptimizeMembershipAdapter implements MembershipPort {

  @Override
  public List<String> mappingRuleIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> groupIds(final MembershipQuery query) {
    // TODO(spike): extract from the OIDC groups claim via CSL OidcGroupsExtractor.
    return List.of();
  }

  @Override
  public List<String> roleIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> tenantIds(final MembershipQuery query) {
    return List.of();
  }
}
