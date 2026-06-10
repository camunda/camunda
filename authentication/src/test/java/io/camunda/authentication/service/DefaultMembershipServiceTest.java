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

    membershipService =
        new DefaultMembershipService(
            mappingRuleServices,
            tenantServices,
            roleServices,
            groupServices,
            securityConfiguration);
  }

  @Test
  void shouldSkipMappingRuleLookupWhenClaimsAreEmpty() {
    // given — BASIC-auth shape: empty claims map, USER principal
    final var authentication =
        membershipService.resolveMemberships(Map.of(), "demo", PrincipalType.USER);

    // when — materialise the lazy field to force the resolver to run
    final var mappingRuleIds = authentication.authenticatedMappingRuleIds();

    // then — the DB lookup was skipped and the field is empty
    assertThat(mappingRuleIds).isEmpty();
    verify(mappingRuleServices, never()).getMatchingMappingRules(any(), any());
  }

  @Test
  void shouldQueryMappingRulesWhenClaimsArePresent() {
    // given — non-empty claims should still flow into the lookup
    when(mappingRuleServices.getMatchingMappingRules(any(), any())).thenReturn(Stream.empty());
    final var authentication =
        membershipService.resolveMemberships(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when — isEmpty() materialises the LazyList, which invokes the supplier
    assertThat(authentication.authenticatedMappingRuleIds()).isEmpty();

    // then
    verify(mappingRuleServices).getMatchingMappingRules(eq(Map.of("sub", "demo")), any());
  }
}
