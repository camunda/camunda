/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.service.MembershipService.PrincipalType;
import io.camunda.authentication.utils.TransientRetry;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
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
  void shouldNotQueryMappingRulesWhenGroupsComeFromOidcClaim() {
    // given — OIDC with groupsClaim configured: group ids come straight from the JWT, no DB
    // lookup is needed. Reading authenticatedGroupIds() on the broker hot path must not drag a
    // getMatchingMappingRules query along for the ride.
    when(oidcAuthenticationConfiguration.isGroupsClaimConfigured()).thenReturn(true);
    when(oidcAuthenticationConfiguration.getGroupsClaim()).thenReturn("groups");
    final var serviceWithGroupsClaim =
        new DefaultMembershipService(
            mappingRuleServices,
            tenantServices,
            roleServices,
            groupServices,
            securityConfiguration);

    final var resolver =
        serviceWithGroupsClaim.newResolver(
            Map.of("sub", "demo", "groups", List.of("g1", "g2")), "demo", PrincipalType.USER);

    // when — only groups are read
    assertThat(resolver.groups()).containsExactlyInAnyOrder("g1", "g2");

    // then — no DB calls at all on this path
    verify(mappingRuleServices, never()).getMatchingMappingRules(any(), any());
    verify(groupServices, never()).getGroupsByMemberTypeAndMemberIds(any(), any());
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

  @Test
  void shouldDegradeMappingRulesToEmptyWhenTheSearchStoreStaysDown() {
    // given
    when(mappingRuleServices.getMatchingMappingRules(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.SEARCH_CLIENT_FAILED, "all shards failed"));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when
    assertThat(resolver.mappingRules()).isEmpty();

    // then — degraded rather than propagated, so the outage does not escape the resolver. These
    // lookups also run during session serialization, where a propagated failure surfaces as an
    // opaque serialization crash at request commit instead of a reportable auth failure.
    verify(mappingRuleServices, times(TransientRetry.MAX_ATTEMPTS))
        .getMatchingMappingRules(any(), any());
  }

  @Test
  void shouldDegradeGroupsToEmptyWhenTheSearchStoreStaysDown() {
    // given
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.SEARCH_SERVER_FAILED, "shards unavailable"));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when
    assertThat(resolver.groups()).isEmpty();

    // then
    verify(groupServices, times(TransientRetry.MAX_ATTEMPTS))
        .getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldDegradeRolesToEmptyWhenTheSearchStoreStaysDown() {
    // given
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.CONNECTION_FAILED, "connection refused"));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when
    assertThat(resolver.roles()).isEmpty();

    // then
    verify(roleServices, times(TransientRetry.MAX_ATTEMPTS))
        .getRolesByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldDegradeTenantsToEmptyWhenTheSearchStoreStaysDown() {
    // given
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.SEARCH_SERVER_FAILED, "shards unavailable"));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when
    assertThat(resolver.tenants()).isEmpty();

    // then
    verify(tenantServices, times(TransientRetry.MAX_ATTEMPTS))
        .getTenantsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldResolveGroupsWhenTheStoreRecoversWithinTheRetryBudget() {
    // given — a single blip, the shape a shard reallocation or a dropped connection actually has
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.CONNECTION_FAILED, "connection refused"))
        .thenReturn(List.of(new GroupEntity(1L, "g1", "group", null)));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when
    assertThat(resolver.groups()).containsExactly("g1");

    // then
    verify(groupServices, times(2)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldPropagateNonTransientSearchFailureWithoutRetrying() {
    // given
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.FORBIDDEN, "forbidden"));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when / then — degrading here would grant the caller a quietly reduced set of memberships for
    // a failure a retry cannot fix; it needs an operator
    assertThatThrownBy(resolver::groups).isInstanceOf(ServiceException.class);
    verify(groupServices, times(1)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldPropagateUnknownReasonFailureWithoutRetrying() {
    // given — UNKNOWN is the reason a CamundaSearchException raised without one carries, which in
    // practice means a deterministic wiring error. The search clients classify every real
    // infrastructure failure explicitly, so UNKNOWN must not be swallowed as "no memberships".
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.UNKNOWN, "no matching controller found"));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when / then
    assertThatThrownBy(resolver::groups).isInstanceOf(ServiceException.class);
    verify(groupServices, times(1)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldPropagatePlainRuntimeExceptionWithoutRetrying() {
    // given — a programming error is not a search-layer failure
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(new IllegalStateException("programming error"));
    final var resolver =
        membershipService.newResolver(Map.of("sub", "demo"), "demo", PrincipalType.USER);

    // when / then
    assertThatThrownBy(resolver::groups).isInstanceOf(IllegalStateException.class);
    verify(groupServices, times(1)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  /**
   * The exception a {@code *Services} lookup actually throws: every search failure is rewrapped by
   * {@link ErrorMapper} on its way out, so stubbing a raw {@link CamundaSearchException} would test
   * a shape the retry never meets in production.
   */
  private static ServiceException translatedSearchFailure(
      final Reason reason, final String message) {
    return ErrorMapper.mapSearchError(new CamundaSearchException(message, reason));
  }
}
