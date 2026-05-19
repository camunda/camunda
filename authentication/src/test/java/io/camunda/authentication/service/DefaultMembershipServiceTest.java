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
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultMembershipServiceTest {

  @Mock private MappingRuleServices mappingRuleServices;
  @Mock private TenantServices tenantServices;
  @Mock private RoleServices roleServices;
  @Mock private GroupServices groupServices;

  private DefaultMembershipService service;

  @BeforeEach
  void setUp() {
    service =
        new DefaultMembershipService(
            mappingRuleServices,
            tenantServices,
            roleServices,
            groupServices,
            new SecurityConfiguration());
  }

  private MembershipQuery baseQuery() {
    return new MembershipQuery(Map.of("sub", "alice"), "alice", PrincipalType.USER);
  }

  @Test
  void mappingRuleIdsReturnsMatchingRules() {
    when(mappingRuleServices.getMatchingMappingRules(any(), any(), any()))
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
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any(), any()))
        .thenReturn(List.of(new GroupEntity(1L, "g1", "group", null)));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    assertThat(service.groupIds(query)).containsExactly("g1");
  }

  @Test
  void roleIdsIncludesGroupsInOwnerMap() {
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any(), any()))
        .thenReturn(List.of(new RoleEntity(1L, "r1", "role", null)));
    final var query = baseQuery().withMappingRuleIds(List.of()).withGroupIds(List.of("g1"));

    assertThat(service.roleIds(query)).containsExactly("r1");
  }

  @Test
  void tenantIdsIncludesGroupsAndRolesInOwnerMap() {
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any(), any()))
        .thenReturn(List.of(new TenantEntity(1L, "t1", "tenant", null)));
    final var query =
        baseQuery()
            .withMappingRuleIds(List.of())
            .withGroupIds(List.of("g1"))
            .withRoleIds(List.of("r1"));

    assertThat(service.tenantIds(query)).containsExactly("t1");
  }
}
