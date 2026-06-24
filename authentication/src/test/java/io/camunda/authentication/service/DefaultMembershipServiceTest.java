/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
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

@ExtendWith(MockitoExtension.class)
class DefaultMembershipServiceTest {

  private static final String TENANT_A = "tenanta";

  @Mock private MappingRuleServices mappingRuleServices;
  @Mock private TenantServices tenantServices;
  @Mock private RoleServices roleServices;
  @Mock private GroupServices groupServices;
  @Mock private GroupServices tenantAGroupServices;
  @Mock private TenantServices tenantATenantServices;
  @Mock private RoleServices tenantARoleServices;
  @Mock private MappingRuleServices tenantAMappingRuleServices;

  private DefaultMembershipService service;

  @BeforeEach
  void setUp() {
    final var serviceRegistry =
        DefaultServiceRegistry.of(
            b ->
                b.mappingRuleServices("default", mappingRuleServices)
                    .groupServices("default", groupServices)
                    .roleServices("default", roleServices)
                    .tenantServices("default", tenantServices)
                    .groupServices(TENANT_A, tenantAGroupServices)
                    .tenantServices(TENANT_A, tenantATenantServices)
                    .roleServices(TENANT_A, tenantARoleServices)
                    .mappingRuleServices(TENANT_A, tenantAMappingRuleServices));
    service = new DefaultMembershipService(serviceRegistry, new CamundaSecurityLibraryProperties());
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @AfterEach
  void clearRequestScope() {
    RequestContextHolder.resetRequestAttributes();
  }

  private MembershipQuery baseQuery() {
    return new MembershipQuery(Map.of("sub", "alice"), "alice", PrincipalType.USER);
  }

  @Test
  void mappingRuleIdsReturnsMatchingRules() {
    when(mappingRuleServices.getMatchingMappingRules(any(), any()))
        .thenReturn(Stream.of(new MappingRuleEntity("mr1", 1L, "claim", "value", "rule")));

    assertThat(service.mappingRuleIds(baseQuery())).containsExactly("mr1");
  }

  @Test
  void mappingRuleIdsReturnsEmptyForBasicAuth() {
    final var query = new MembershipQuery(Map.of(), "alice", PrincipalType.USER);
    assertThat(service.mappingRuleIds(query)).isEmpty();
  }

  @Test
  void groupIdsLooksUpGroupsFromDb() {
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new GroupEntity(1L, "g1", "group", null)));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    assertThat(service.groupIds(query)).containsExactly("g1");
  }

  @Test
  void roleIdsIncludesGroupsInOwnerMap() {
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new RoleEntity(1L, "r1", "role", null)));
    final var query = baseQuery().withMappingRuleIds(List.of()).withGroupIds(List.of("g1"));

    assertThat(service.roleIds(query)).containsExactly("r1");
  }

  @Test
  void tenantIdsIncludesGroupsAndRolesInOwnerMap() {
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new TenantEntity(1L, "t1", "tenant", null)));
    final var query =
        baseQuery()
            .withMappingRuleIds(List.of())
            .withGroupIds(List.of("g1"))
            .withRoleIds(List.of("r1"));

    assertThat(service.tenantIds(query)).containsExactly("t1");
  }

  @Test
  void shouldRouteGroupLookupToRequestPhysicalTenant() {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, TENANT_A);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(tenantAGroupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new GroupEntity(1L, "ga1", "group", null)));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    assertThat(service.groupIds(query)).containsExactly("ga1");
    verifyNoInteractions(groupServices);
  }

  @Test
  void shouldRouteTenantLookupToRequestPhysicalTenant() {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, TENANT_A);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(tenantATenantServices.getTenantsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new TenantEntity(1L, "ta1", "tenant", null)));
    final var query =
        baseQuery()
            .withMappingRuleIds(List.of())
            .withGroupIds(List.of("g1"))
            .withRoleIds(List.of("r1"));

    assertThat(service.tenantIds(query)).containsExactly("ta1");
    verifyNoInteractions(tenantServices);
  }

  @Test
  void shouldRouteRoleLookupToRequestPhysicalTenant() {
    // given
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, TENANT_A);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(tenantARoleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new RoleEntity(1L, "ra1", "role", null)));
    final var query = baseQuery().withMappingRuleIds(List.of()).withGroupIds(List.of("g1"));

    // when
    final var result = service.roleIds(query);

    // then
    assertThat(result).containsExactly("ra1");
    verifyNoInteractions(roleServices);
  }

  @Test
  void shouldRouteMappingRuleLookupToRequestPhysicalTenant() {
    // given
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, TENANT_A);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(tenantAMappingRuleServices.getMatchingMappingRules(any(), any()))
        .thenReturn(Stream.of(new MappingRuleEntity("mra1", 1L, "claim", "value", "rule")));

    // when
    final var result = service.mappingRuleIds(baseQuery());

    // then
    assertThat(result).containsExactly("mra1");
    verifyNoInteractions(mappingRuleServices);
  }
}
