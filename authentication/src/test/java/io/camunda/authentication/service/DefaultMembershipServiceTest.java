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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.service.MembershipService.PrincipalType;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DefaultMembershipServiceTest {

  @Mock private MappingRuleServices mappingRuleServices;
  @Mock private TenantServices tenantServices;
  @Mock private RoleServices roleServices;
  @Mock private GroupServices groupServices;
  @Mock private SecurityConfiguration securityConfiguration;
  @Mock private AuthenticationConfiguration authenticationConfiguration;
  @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

  private DefaultMembershipService membershipService;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
    when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any())).thenReturn(List.of());
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any())).thenReturn(List.of());
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any())).thenReturn(List.of());
    when(mappingRuleServices.getMatchingMappingRules(any(), any())).thenReturn(Stream.empty());

    membershipService =
        new DefaultMembershipService(
            mappingRuleServices,
            tenantServices,
            roleServices,
            groupServices,
            securityConfiguration);
  }

  @Test
  void shouldNotInvokeAnyServiceUntilResolverIsRead() {
    // given
    membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // then — constructing the resolver alone does not trigger DB calls
    verify(mappingRuleServices, never()).getMatchingMappingRules(any(), any());
    verify(groupServices, never()).getGroupsByMemberTypeAndMemberIds(any(), any());
    verify(roleServices, never()).getRolesByMemberTypeAndMemberIds(any(), any());
    verify(tenantServices, never()).getTenantsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldQueryMappingRulesWhenAskedAndPassThroughClaims() {
    // given
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when
    assertThat(resolver.mappingRules()).isEmpty();

    // then
    verify(mappingRuleServices).getMatchingMappingRules(eq(Map.of("sub", "demo")), any());
  }

  @Test
  void shouldMemoizeMembershipLookups() {
    // given
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new RoleEntity(1L, "role1", "role", "desc")));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when — read roles twice and groups three times (groups is a prerequisite of roles)
    resolver.roles();
    resolver.roles();
    resolver.groups();
    resolver.groups();
    resolver.groups();

    // then — each underlying service is called exactly once
    verify(mappingRuleServices).getMatchingMappingRules(any(), any());
    verify(groupServices).getGroupsByMemberTypeAndMemberIds(any(), any());
    verify(roleServices).getRolesByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void basicAuthOverloadShouldUseEmptyClaimsAndUserPrincipal() {
    // given — convenience overload for the BASIC-auth callers
    final var resolver = membershipService.newResolver("demo");

    // when
    resolver.groups();

    // then — the resolver is constructed against empty claims as a USER principal, so the
    // group lookup runs against an ownerType map seeded only with the USER entry
    verify(mappingRuleServices, never()).getMatchingMappingRules(any(), any());
    verify(groupServices).getGroupsByMemberTypeAndMemberIds(any(), any());
  }
}
