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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.authentication.active.ActiveUserChecker;
import io.camunda.authentication.source.LoginSources;
import io.camunda.authentication.utils.TransientRetry;
import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MappingRuleServices.SourcedAttributes;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.test.appender.ListAppender;
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

  // Never used on the search path — GroupServices only needs it to be non-null at construction.
  private static final ApiServicesExecutorProvider EXECUTOR_PROVIDER =
      new ApiServicesExecutorProvider(Executors.newVirtualThreadPerTaskExecutor());

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

  /**
   * The exception a {@code *Services} lookup actually throws: every search failure is rewrapped by
   * {@link ErrorMapper} on its way out, so stubbing a raw {@link CamundaSearchException} would test
   * a shape the retry never meets in production.
   */
  private static ServiceException translatedSearchFailure(
      final Reason reason, final String message) {
    return ErrorMapper.mapSearchError(new CamundaSearchException(message, reason));
  }

  @Test
  void mappingRuleIdsReturnsMatchingRules() {
    when(mappingRuleServices.getMatchingMappingRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(Stream.of(new MappingRuleEntity("mr1", 1L, "claim", "value", "rule")));

    assertThat(service.mappingRuleIds(baseQuery())).containsExactly("mr1");
  }

  @Test
  void mappingRuleIdsPassesStoredUserAttributesToTheMatching() {
    // given
    final var userServices = mock(UserServices.class);
    when(userServices.getUser(eq("alice"), any()))
        .thenReturn(
            new UserEntity(
                1L, "alice", "n", "e", "p", null, true, Map.of("department", "Finance")));
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(
            List.of(
                new GroupEntity(1L, "g1", "group", "g", null, Map.of("region", "EMEA"), null, null),
                new GroupEntity(2L, "g2", "empty", "g", null, Map.of(), null, null)));
    final var withLookup = serviceWithUserLookup(userServices);
    when(mappingRuleServices.getMatchingMappingRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(Stream.of(new MappingRuleEntity("mr1", 1L, "$.department", "Finance", "r")));

    // when
    final var result = withLookup.mappingRuleIds(baseQuery());

    // then - attributes come from storage, claims stay separate
    assertThat(result).containsExactly("mr1");
    verify(mappingRuleServices)
        .getMatchingMappingRules(
            eq(Map.of("sub", "alice")),
            eq(Map.of("department", "Finance")),
            eq(List.of(new SourcedAttributes(null, Map.of("region", "EMEA")))),
            isNull(),
            isNull(),
            any());
  }

  @Test
  void mappingRuleIdsPassesLoginSourceAndSourcesOfUserAndGroupsToTheMatching() {
    // given
    final var userServices = mock(UserServices.class);
    when(userServices.getUser(eq("alice"), any()))
        .thenReturn(
            new UserEntity(
                1L,
                "alice",
                "n",
                "e",
                "p",
                "ext",
                true,
                Map.of("department", "Finance"),
                null,
                null,
                "keycloak"));
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(
            List.of(
                new GroupEntity(
                    1L,
                    "g1",
                    "group",
                    "g",
                    null,
                    Map.of("region", "EMEA"),
                    null,
                    null,
                    "keycloak")));
    final var withLookup = serviceWithUserLookup(userServices);
    when(mappingRuleServices.getMatchingMappingRules(any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> Stream.empty());
    final var query =
        new MembershipQuery(
            Map.of("sub", "alice", LoginSources.LOGIN_SOURCE_CLAIM, "entra"),
            "alice",
            PrincipalType.USER);

    // when
    withLookup.mappingRuleIds(query);

    // then - the login source is kept out of the token claims
    verify(mappingRuleServices)
        .getMatchingMappingRules(
            eq(Map.of("sub", "alice")),
            eq(Map.of("department", "Finance")),
            eq(List.of(new SourcedAttributes("keycloak", Map.of("region", "EMEA")))),
            eq("entra"),
            eq("keycloak"),
            any());
  }

  @Test
  void mappingRuleIdsUsesNoAttributesWhenUserLookupFailsOrIsAbsent() {
    // given
    final var userServices = mock(UserServices.class);
    when(userServices.getUser(eq("alice"), any()))
        .thenThrow(new ServiceException("down", ServiceException.Status.UNAVAILABLE));
    when(mappingRuleServices.getMatchingMappingRules(any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> Stream.empty());

    // when
    serviceWithUserLookup(userServices).mappingRuleIds(baseQuery());
    service.mappingRuleIds(baseQuery());

    // then
    verify(mappingRuleServices, times(2))
        .getMatchingMappingRules(
            eq(Map.of("sub", "alice")), eq(Map.of()), eq(List.of()), isNull(), isNull(), any());
  }

  @Test
  void mappingRuleIdsNeverLooksUpAttributesForClients() {
    // given
    final var userServices = mock(UserServices.class);
    when(mappingRuleServices.getMatchingMappingRules(any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> Stream.empty());

    // when
    serviceWithUserLookup(userServices)
        .mappingRuleIds(new MembershipQuery(Map.of("sub", "c"), "client", PrincipalType.CLIENT));

    // then
    verifyNoInteractions(userServices);
  }

  private DefaultMembershipService serviceWithUserLookup(final UserServices userServices) {
    final var registry =
        DefaultServiceRegistry.of(
            b ->
                b.mappingRuleServices("default", mappingRuleServices)
                    .userServices("default", userServices)
                    .groupServices("default", groupServices));
    return new DefaultMembershipService(
        registry,
        new CamundaSecurityLibraryProperties(),
        new ActiveUserChecker(() -> registry, Duration.ofSeconds(30)));
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
  void groupIdsReturnsEmptyWhenGroupsClaimIsMalformed() {
    // given — groups-claim configured, but this token's groups claim is a scalar number, not a
    // string or a list of strings
    final var serviceWithGroupsClaim = serviceWithGroupsClaim("groups");
    final var query = new MembershipQuery(Map.of("groups", 123), "alice", PrincipalType.USER);

    // when/then — extraction fails safely; no exception, no DB lookup either
    assertThat(serviceWithGroupsClaim.groupIds(query)).isEmpty();
    verifyNoInteractions(groupServices);
  }

  @Test
  void groupIdsReturnsEmptyWhenGroupsClaimResolvesToNestedObject() {
    // given — groups-claim misconfigured one level too shallow ("realm_access" instead of
    // "realm_access.roles"), resolving to a Map rather than a string/list
    final var serviceWithGroupsClaim = serviceWithGroupsClaim("realm_access");
    final var query =
        new MembershipQuery(
            Map.of("realm_access", Map.of("roles", List.of("eng"))), "alice", PrincipalType.USER);

    // when/then
    assertThat(serviceWithGroupsClaim.groupIds(query)).isEmpty();
    verifyNoInteractions(groupServices);
  }

  @Test
  void groupIdsFallsBackToEmptyWhenTheRealServiceRewrapsTheSearchFailure() {
    // given — a real GroupServices, so the failure travels the production path through
    // SearchQueryService.executeSearchRequest, which rewraps the CamundaSearchException before the
    // retry ever sees it. A service mocked to throw the raw exception skips that boundary and
    // cannot show whether the retry survives it.
    final var searchClient = mock(GroupSearchClient.class);
    when(searchClient.withSecurityContext(any())).thenReturn(searchClient);
    when(searchClient.searchGroups(any()))
        .thenThrow(new CamundaSearchException("all shards failed", Reason.SEARCH_CLIENT_FAILED));
    final var serviceUnderTest = serviceWithRealGroupServices(searchClient);
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    // when / then
    assertThat(serviceUnderTest.groupIds(query)).isEmpty();
    verify(searchClient, times(TransientRetry.MAX_ATTEMPTS)).searchGroups(any());
  }

  private DefaultMembershipService serviceWithRealGroupServices(
      final GroupSearchClient searchClient) {
    final var realGroupServices =
        new GroupServices(
            "default", null, new SecurityContextProvider(), searchClient, EXECUTOR_PROVIDER, null);
    final var registry =
        DefaultServiceRegistry.of(
            b ->
                b.mappingRuleServices("default", mappingRuleServices)
                    .groupServices("default", realGroupServices)
                    .roleServices("default", roleServices)
                    .tenantServices("default", tenantServices));
    return new DefaultMembershipService(registry, new CamundaSecurityLibraryProperties());
  }

  @Test
  void groupIdsFallsBackToEmptyAfterExhaustingRetriesOnTransientFailure() {
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.SEARCH_SERVER_FAILED, "shards unavailable"));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    assertThat(service.groupIds(query)).isEmpty();
    verify(groupServices, times(3)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void groupIdsRecoversAfterTransientFailureWithinRetryBudget() {
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.CONNECTION_FAILED, "connection refused"))
        .thenReturn(List.of(new GroupEntity(1L, "g1", "group", null)));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    assertThat(service.groupIds(query)).containsExactly("g1");
    verify(groupServices, times(2)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void groupIdsPropagatesNonTransientFailureWithoutRetry() {
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.INVALID_ARGUMENT, "invalid query"));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    assertThatThrownBy(() -> service.groupIds(query)).isInstanceOf(ServiceException.class);
    verify(groupServices, times(1)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void groupIdsPropagatesGenericRuntimeExceptionWithoutRetry() {
    // a plain RuntimeException (e.g. a missing PhysicalTenantContext, or any other programming
    // error) is not a search-layer failure and must never be swallowed as "no memberships found"
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(new IllegalStateException("no PhysicalTenantContext bound to this thread"));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    assertThatThrownBy(() -> service.groupIds(query)).isInstanceOf(IllegalStateException.class);
    verify(groupServices, times(1)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void groupIdsPropagatesUnknownReasonFailureWithoutRetry() {
    // UNKNOWN is the default reason for a CamundaSearchException raised without one, which in
    // practice means a deterministic wiring or request-construction error (e.g. no matching
    // resource-access controller). The search clients always classify real infrastructure
    // failures explicitly, so UNKNOWN must not be swallowed as "no memberships found".
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(
            translatedSearchFailure(
                Reason.UNKNOWN, "no matching resource access controller found"));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    assertThatThrownBy(() -> service.groupIds(query)).isInstanceOf(ServiceException.class);
    verify(groupServices, times(1)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void roleIdsIncludesGroupsInOwnerMap() {
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new RoleEntity(1L, "r1", "role", null)));
    final var query = baseQuery().withMappingRuleIds(List.of()).withGroupIds(List.of("g1"));

    assertThat(service.roleIds(query)).containsExactly("r1");
  }

  @Test
  void roleIdsFallsBackToEmptyAfterExhaustingRetriesOnTransientFailure() {
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.SEARCH_SERVER_FAILED, "shards unavailable"));
    final var query = baseQuery().withMappingRuleIds(List.of()).withGroupIds(List.of("g1"));

    assertThat(service.roleIds(query)).isEmpty();
    verify(roleServices, times(3)).getRolesByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void roleIdsPropagatesNonTransientFailureWithoutRetry() {
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.FORBIDDEN, "forbidden"));
    final var query = baseQuery().withMappingRuleIds(List.of()).withGroupIds(List.of("g1"));

    assertThatThrownBy(() -> service.roleIds(query)).isInstanceOf(ServiceException.class);
    verify(roleServices, times(1)).getRolesByMemberTypeAndMemberIds(any(), any());
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
  void tenantIdsFallsBackToEmptyAfterExhaustingRetriesOnTransientFailure() {
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.SEARCH_SERVER_FAILED, "shards unavailable"));
    final var query =
        baseQuery()
            .withMappingRuleIds(List.of())
            .withGroupIds(List.of("g1"))
            .withRoleIds(List.of("r1"));

    assertThat(service.tenantIds(query)).isEmpty();
    verify(tenantServices, times(3)).getTenantsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void tenantIdsPropagatesNonTransientFailureWithoutRetry() {
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.NOT_FOUND, "not found"));
    final var query =
        baseQuery()
            .withMappingRuleIds(List.of())
            .withGroupIds(List.of("g1"))
            .withRoleIds(List.of("r1"));

    assertThatThrownBy(() -> service.tenantIds(query)).isInstanceOf(ServiceException.class);
    verify(tenantServices, times(1)).getTenantsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void mappingRuleIdsFallsBackToEmptyAfterExhaustingRetriesOnTransientFailure() {
    when(mappingRuleServices.getMatchingMappingRules(any(), any(), any(), any(), any(), any()))
        .thenThrow(translatedSearchFailure(Reason.SEARCH_SERVER_FAILED, "shards unavailable"));

    assertThat(service.mappingRuleIds(baseQuery())).isEmpty();
    verify(mappingRuleServices, times(3))
        .getMatchingMappingRules(any(), any(), any(), any(), any(), any());
  }

  @Test
  void mappingRuleIdsPropagatesNonTransientFailureWithoutRetry() {
    when(mappingRuleServices.getMatchingMappingRules(any(), any(), any(), any(), any(), any()))
        .thenThrow(translatedSearchFailure(Reason.NOT_UNIQUE, "not unique"));

    assertThatThrownBy(() -> service.mappingRuleIds(baseQuery()))
        .isInstanceOf(ServiceException.class);
    verify(mappingRuleServices, times(1))
        .getMatchingMappingRules(any(), any(), any(), any(), any(), any());
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
    when(tenantAMappingRuleServices.getMatchingMappingRules(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(Stream.of(new MappingRuleEntity("mra1", 1L, "claim", "value", "rule")));

    // when
    final var result = service.mappingRuleIds(baseQuery());

    // then
    assertThat(result).containsExactly("mra1");
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldReportAnOutageOncePerPhysicalTenant() {
    // given — the group store is down for tenant A while the default tenant's is healthy
    when(tenantAGroupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(translatedSearchFailure(Reason.SEARCH_SERVER_FAILED, "shards unavailable"));
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new GroupEntity(1L, "g1", "group", null)));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    // when — tenant A's outage is interleaved with healthy traffic for the default tenant, as it
    // would be on a shared gateway
    final var events = new ArrayList<LogEvent>();
    withLogCapture(
        events,
        () -> {
          inPhysicalTenant(TENANT_A, () -> service.groupIds(query));
          inPhysicalTenant("default", () -> service.groupIds(query));
          inPhysicalTenant(TENANT_A, () -> service.groupIds(query));
        });

    // then — one report for tenant A's outage. A healthy call for another tenant must not close it,
    // or every interleaved request would re-report the same ongoing outage.
    assertThat(events)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getMessage().getFormattedMessage())
                    .contains(TENANT_A)
                    .contains("groupIds"));
  }

  @Test
  void shouldNotRetainAnOutageEntryForALookupThatNeverFailed() {
    // given — a healthy group store
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new GroupEntity(1L, "g1", "group", null)));
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    // when — the lookup succeeds
    inPhysicalTenant("default", () -> service.groupIds(query));

    // then — nothing is retained. The key is derived from the physical tenant on the request, which
    // PhysicalTenantFilter stamps without validating, so anything retained on the success path
    // would tie this service's memory to the set of tenant ids that reach it rather than to the set
    // that is configured.
    assertThat(service.lookupOutages).isEmpty();
  }

  @Test
  void shouldNotRetainAnOutageEntryForAnUnconfiguredPhysicalTenant() {
    // given — a physical tenant this registry does not know. PhysicalTenantFilter stamps the id
    // from the request path without validating it, so an arbitrary one does reach this service;
    // what stops it is the lookup opening by resolving that tenant's services.
    final var query = baseQuery().withMappingRuleIds(List.of("mr1"));

    // when / then — the registry rejects it before any search runs, and a rejection is not a
    // transient failure, so it propagates
    assertThatThrownBy(() -> inPhysicalTenant("tenantz", () -> service.groupIds(query)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantz");

    // and nothing is retained. This is what bounds the map by the configured tenants rather than
    // by the ids that arrive: an id no tenant matches cannot reach the failure branch that
    // allocates.
    assertThat(service.lookupOutages).isEmpty();
  }

  private void inPhysicalTenant(final String physicalTenantId, final Runnable call) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    call.run();
  }

  private static void withLogCapture(final List<LogEvent> sink, final Runnable body) {
    final var loggerName = DefaultMembershipService.class.getName();
    final var appender = new ListAppender("membership-outage-appender");
    appender.start();
    final var context = (LoggerContext) LogManager.getContext(false);
    final var loggerConfig = new LoggerConfig(loggerName, Level.ALL, true);
    loggerConfig.addAppender(appender, null, null);
    context.getConfiguration().addLogger(loggerName, loggerConfig);
    context.updateLoggers();
    try {
      body.run();
      sink.addAll(appender.getEvents());
    } finally {
      context.getConfiguration().removeLogger(loggerName);
      context.updateLoggers();
      appender.stop();
    }
  }

  private DefaultMembershipService serviceWithGroupsClaim(final String claim) {
    final var config = new CamundaSecurityLibraryProperties();
    config.getAuthentication().getOidc().setGroupsClaim(claim);
    final var serviceRegistry =
        DefaultServiceRegistry.of(
            b ->
                b.mappingRuleServices("default", mappingRuleServices)
                    .groupServices("default", groupServices)
                    .roleServices("default", roleServices)
                    .tenantServices("default", tenantServices));
    return new DefaultMembershipService(serviceRegistry, config);
  }
}
