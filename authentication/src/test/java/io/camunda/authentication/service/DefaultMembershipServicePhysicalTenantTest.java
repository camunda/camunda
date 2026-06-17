/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * SPIKE (ADR-0005) <b>A.1 proof</b>: request-time identity and membership resolution routes to the
 * in-context physical tenant via {@link PhysicalTenantContext#current()}.
 *
 * <p>These tests exercise the <b>real</b> thread-bound context (not a stubbed supplier) to confirm
 * that each {@link DefaultMembershipService} lookup method dispatches to the {@link
 * DefaultServiceRegistry} entry for the in-context tenant ("tenant-b"), and never touches the
 * "default" tenant's services.
 */
@ExtendWith(MockitoExtension.class)
final class DefaultMembershipServicePhysicalTenantTest {

  // "tenant-b" services — the ones that must be invoked
  @Mock private MappingRuleServices tenantBMappingRuleServices;
  @Mock private GroupServices tenantBGroupServices;
  @Mock private RoleServices tenantBRoleServices;
  @Mock private TenantServices tenantBTenantServices;

  // "default" services — must never be invoked
  @Mock private MappingRuleServices defaultMappingRuleServices;
  @Mock private GroupServices defaultGroupServices;
  @Mock private RoleServices defaultRoleServices;
  @Mock private TenantServices defaultTenantServices;

  private DefaultMembershipService service;

  @BeforeEach
  void setUp() {
    final var serviceRegistry =
        DefaultServiceRegistry.of(
            b ->
                b.mappingRuleServices("default", defaultMappingRuleServices)
                    .groupServices("default", defaultGroupServices)
                    .roleServices("default", defaultRoleServices)
                    .tenantServices("default", defaultTenantServices)
                    .mappingRuleServices("tenant-b", tenantBMappingRuleServices)
                    .groupServices("tenant-b", tenantBGroupServices)
                    .roleServices("tenant-b", tenantBRoleServices)
                    .tenantServices("tenant-b", tenantBTenantServices));
    // CamundaSecurityLibraryProperties defaults: no groups-claim configured, so groupIds() hits
    // the DB path (groupServices) rather than in-memory OIDC extraction.
    service = new DefaultMembershipService(serviceRegistry, new CamundaSecurityLibraryProperties());
  }

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldRouteMappingRuleIdsToPhysicalTenantInContext() {
    // given — token claims non-empty so mappingRuleIds() does not short-circuit on empty claims
    when(tenantBMappingRuleServices.getMatchingMappingRules(any(), any()))
        .thenReturn(Stream.empty());
    bindRequestWithPhysicalTenant("tenant-b");
    final var query = new MembershipQuery(Map.of("sub", "alice"), "alice", PrincipalType.USER);

    // when
    service.mappingRuleIds(query);

    // then
    verify(tenantBMappingRuleServices).getMatchingMappingRules(any(), any());
    verify(defaultMappingRuleServices, never()).getMatchingMappingRules(any(), any());
  }

  @Test
  void shouldRouteGroupIdsToPhysicalTenantInContext() {
    // given — groups-claim not configured, so groupIds() falls through to DB lookup
    when(tenantBGroupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of());
    bindRequestWithPhysicalTenant("tenant-b");
    final var query = new MembershipQuery(Map.of("sub", "alice"), "alice", PrincipalType.USER);

    // when
    service.groupIds(query);

    // then
    verify(tenantBGroupServices).getGroupsByMemberTypeAndMemberIds(any(), any());
    verify(defaultGroupServices, never()).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldRouteRoleIdsToPhysicalTenantInContext() {
    // given
    when(tenantBRoleServices.getRolesByMemberTypeAndMemberIds(any(), any())).thenReturn(List.of());
    bindRequestWithPhysicalTenant("tenant-b");
    final var query =
        new MembershipQuery(Map.of("sub", "alice"), "alice", PrincipalType.USER)
            .withMappingRuleIds(List.of())
            .withGroupIds(List.of());

    // when
    service.roleIds(query);

    // then
    verify(tenantBRoleServices).getRolesByMemberTypeAndMemberIds(any(), any());
    verify(defaultRoleServices, never()).getRolesByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldRouteTenantIdsToPhysicalTenantInContext() {
    // given
    when(tenantBTenantServices.getTenantsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of());
    bindRequestWithPhysicalTenant("tenant-b");
    final var query =
        new MembershipQuery(Map.of("sub", "alice"), "alice", PrincipalType.USER)
            .withMappingRuleIds(List.of())
            .withGroupIds(List.of())
            .withRoleIds(List.of());

    // when
    service.tenantIds(query);

    // then
    verify(tenantBTenantServices).getTenantsByMemberTypeAndMemberIds(any(), any());
    verify(defaultTenantServices, never()).getTenantsByMemberTypeAndMemberIds(any(), any());
  }

  private static void bindRequestWithPhysicalTenant(final String physicalTenantId) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
