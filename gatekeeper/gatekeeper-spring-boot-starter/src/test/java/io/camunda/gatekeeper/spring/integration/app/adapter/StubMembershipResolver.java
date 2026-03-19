/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration.app.adapter;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.PrincipalType;
import io.camunda.gatekeeper.spi.MembershipResolver;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Stub membership resolver that simulates a real user store. In production this would query a
 * database or directory service for the principal's group, role, and tenant memberships.
 */
@Component
public final class StubMembershipResolver implements MembershipResolver {

  public static final List<String> DEMO_GROUPS = List.of("engineering", "admins");
  public static final List<String> DEMO_ROLES = List.of("operator");
  public static final List<String> DEMO_TENANTS = List.of("tenant-alpha", "tenant-beta");

  public static final List<String> OPERATOR_GROUPS = List.of("ops");
  public static final List<String> OPERATOR_ROLES = List.of("viewer");
  public static final List<String> OPERATOR_TENANTS = List.of("tenant-alpha");

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    return switch (principalId) {
      case "demo" ->
          CamundaAuthentication.of(
              b ->
                  b.user(principalId)
                      .groupIds(DEMO_GROUPS)
                      .roleIds(DEMO_ROLES)
                      .tenants(DEMO_TENANTS)
                      .claims(tokenClaims));
      case "operator" ->
          CamundaAuthentication.of(
              b ->
                  b.user(principalId)
                      .groupIds(OPERATOR_GROUPS)
                      .roleIds(OPERATOR_ROLES)
                      .tenants(OPERATOR_TENANTS)
                      .claims(tokenClaims));
      default -> CamundaAuthentication.of(b -> b.user(principalId).claims(tokenClaims));
    };
  }
}
