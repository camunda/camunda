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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
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

  @Test
  void providerExposesAllFourMembershipTypesFromTheChain() {
    when(mappingRuleServices.getMatchingMappingRules(any(), any()))
        .thenReturn(Stream.of(new MappingRuleEntity("mr1", 1L, "claim", "value", "rule")));
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new GroupEntity(1L, "g1", "group", null)));
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new RoleEntity(1L, "r1", "role", null)));
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new TenantEntity(1L, "t1", "tenant", null)));

    final var provider =
        service.createProvider(Map.of("sub", "alice"), "alice", PrincipalType.USER);

    assertThat(provider.groups()).containsExactly("g1");
    assertThat(provider.roles()).containsExactly("r1");
    assertThat(provider.tenants()).containsExactly("t1");
    assertThat(provider.mappingRules()).containsExactly("mr1");
  }

  @Test
  void providerInvokesNoServicesUntilAFieldIsRead() {
    service.createProvider(Map.of("sub", "alice"), "alice", PrincipalType.USER);

    verify(mappingRuleServices, never()).getMatchingMappingRules(any(), any());
    verify(groupServices, never()).getGroupsByMemberTypeAndMemberIds(any(), any());
    verify(roleServices, never()).getRolesByMemberTypeAndMemberIds(any(), any());
    verify(tenantServices, never()).getTenantsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void readingGroupsTriggersOnlyTheMappingRulesAndGroupsQueries() {
    when(mappingRuleServices.getMatchingMappingRules(any(), any())).thenReturn(Stream.empty());
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any())).thenReturn(List.of());

    final var provider =
        service.createProvider(Map.of("sub", "alice"), "alice", PrincipalType.USER);
    provider.groups();

    verify(mappingRuleServices).getMatchingMappingRules(any(), any());
    verify(groupServices).getGroupsByMemberTypeAndMemberIds(any(), any());
    verify(roleServices, never()).getRolesByMemberTypeAndMemberIds(any(), any());
    verify(tenantServices, never()).getTenantsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void providerForUserSkipsMappingRulesAndLooksUpGroupsRolesTenants() {
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new GroupEntity(1L, "g1", "group", null)));
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new RoleEntity(1L, "r1", "role", null)));
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new TenantEntity(1L, "t1", "tenant", null)));

    final var provider = service.createProviderForUser("alice");

    assertThat(provider.groups()).containsExactly("g1");
    assertThat(provider.roles()).containsExactly("r1");
    assertThat(provider.tenants()).containsExactly("t1");
    assertThat(provider.mappingRules()).isEmpty();
    verify(mappingRuleServices, never()).getMatchingMappingRules(any(), any());
  }
}
