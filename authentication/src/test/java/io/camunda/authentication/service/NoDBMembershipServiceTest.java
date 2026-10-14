/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.pt.PhysicalTenantAuthConfigurations;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class NoDBMembershipServiceTest {

  private static final String TENANT_A = "tenanta";
  private static final String ROOT_ISSUER = "https://root.example.com/realms/camunda";
  private static final String OKTA_ISSUER = "https://okta.example.com/oauth2/default";
  private static final String ROOT = "camunda.security.authentication";
  private static final String TENANT_A_PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication";

  @AfterEach
  void clearRequestScope() {
    RequestContextHolder.resetRequestAttributes();
  }

  private static MembershipQuery query(final Map<String, Object> claims) {
    return new MembershipQuery(claims, "alice", PrincipalType.USER);
  }

  @Test
  void groupIdsExtractsFromClaimsWhenConfigured() {
    final var service = serviceWithGroupsClaim("$.groups");
    assertThat(service.groupIds(query(Map.of("sub", "alice", "groups", List.of("eng", "ops")))))
        .containsExactlyInAnyOrder("eng", "ops");
  }

  @Test
  void groupIdsReturnsEmptyWhenNoGroupsClaimConfigured() {
    final var service =
        new NoDBMembershipService(new CamundaSecurityLibraryProperties(), new MockEnvironment());
    assertThat(service.groupIds(query(Map.of("groups", List.of("eng"))))).isEmpty();
  }

  @Test
  void groupIdsReturnsEmptyWhenGroupsClaimIsMalformed() {
    // given — groups-claim configured, but this token's groups claim is a scalar number, not a
    // string or a list of strings
    final var service = serviceWithGroupsClaim("groups");

    // when/then — extraction fails safely; no exception
    assertThat(service.groupIds(query(Map.of("groups", 123)))).isEmpty();
  }

  @Test
  void groupIdsReturnsEmptyWhenGroupsClaimResolvesToNestedObject() {
    // given — groups-claim misconfigured one level too shallow ("realm_access" instead of
    // "realm_access.roles"), resolving to a Map rather than a string/list
    final var service = serviceWithGroupsClaim("realm_access");

    // when/then
    assertThat(service.groupIds(query(Map.of("realm_access", Map.of("roles", List.of("eng"))))))
        .isEmpty();
  }

  @Test
  void allOtherMethodsReturnEmpty() {
    final var service =
        new NoDBMembershipService(new CamundaSecurityLibraryProperties(), new MockEnvironment());
    final var q = query(Map.of());
    assertThat(service.mappingRuleIds(q)).isEmpty();
    assertThat(service.roleIds(q)).isEmpty();
    assertThat(service.tenantIds(q)).isEmpty();
  }

  @Test
  void shouldReadGroupsFromClaimOfIssuingProvider() {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim", "groups",
                ROOT + ".providers.oidc.okta.issuer-uri", OKTA_ISSUER,
                ROOT + ".providers.oidc.okta.groups-claim", "roles"));

    // when
    final var groups = service.groupIds(query(oktaToken()));

    // then
    assertThat(groups).containsExactly("okta-role");
  }

  @Test
  void shouldReadGroupsFromRootClaimForProviderWithoutOwnClaim() {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim",
                "groups",
                ROOT + ".providers.oidc.okta.issuer-uri",
                OKTA_ISSUER));

    // when
    final var groups = service.groupIds(query(oktaToken()));

    // then
    assertThat(groups).containsExactly("okta-group");
  }

  @Test
  void shouldReadGroupsFromTenantClaimForProviderWithoutOwnClaim() {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim", "groups",
                ROOT + ".providers.oidc.okta.issuer-uri", OKTA_ISSUER,
                TENANT_A_PREFIX + ".oidc.groups-claim", "teams"));
    final var token = Map.<String, Object>of("iss", OKTA_ISSUER, "teams", List.of("okta-team"));

    // when
    final var groups = inPhysicalTenant(TENANT_A, () -> service.groupIds(query(token)));

    // then
    assertThat(groups).containsExactly("okta-team");
  }

  @Test
  void shouldReadGroupsFromTenantClaimForUnknownIssuer() {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim", "groups",
                ROOT + ".providers.oidc.okta.issuer-uri", OKTA_ISSUER,
                ROOT + ".providers.oidc.okta.groups-claim", "roles"));
    final var token =
        Map.<String, Object>of(
            "iss", "https://unknown.example.com",
            "groups", List.of("group"),
            "roles", List.of("role"));

    // when
    final var groups = service.groupIds(query(token));

    // then
    assertThat(groups).containsExactly("group");
  }

  @Test
  void shouldIgnoreTokenGroupsWhenTenantUsesCamundaGroups() {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim", "groups",
                ROOT + ".oidc.issuer-uri", ROOT_ISSUER,
                ROOT + ".oidc.client-id", "camunda",
                TENANT_A_PREFIX + ".oidc.groups-claim", ""));
    final var token = Map.<String, Object>of("iss", ROOT_ISSUER, "groups", List.of("root-group"));

    // when
    final var tenantGroups = inPhysicalTenant(TENANT_A, () -> service.groupIds(query(token)));
    final var defaultGroups = inPhysicalTenant("default", () -> service.groupIds(query(token)));

    // then
    assertThat(tenantGroups).isEmpty();
    assertThat(defaultGroups).containsExactly("root-group");
  }

  @Test
  void shouldIgnoreProviderClaimWhenTenantUsesCamundaGroups() {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim", "groups",
                ROOT + ".providers.oidc.okta.issuer-uri", OKTA_ISSUER,
                ROOT + ".providers.oidc.okta.groups-claim", "roles",
                TENANT_A_PREFIX + ".oidc.groups-claim", ""));

    // when
    final var groups = inPhysicalTenant(TENANT_A, () -> service.groupIds(query(oktaToken())));

    // then
    assertThat(groups).isEmpty();
  }

  @Test
  void shouldReadGroupsFromTenantClaimForProviderNotAssignedToTenant() {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim", "groups",
                ROOT + ".oidc.issuer-uri", ROOT_ISSUER,
                ROOT + ".oidc.client-id", "camunda",
                ROOT + ".providers.oidc.okta.issuer-uri", OKTA_ISSUER,
                ROOT + ".providers.oidc.okta.groups-claim", "roles",
                TENANT_A_PREFIX + ".providers.assigned[0]", "oidc"));

    // when
    final var groups = inPhysicalTenant(TENANT_A, () -> service.groupIds(query(oktaToken())));

    // then
    assertThat(groups).containsExactly("okta-group");
  }

  @Test
  void shouldMatchIssuerOfIdTokenGivenAsUrl() throws Exception {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim", "groups",
                ROOT + ".providers.oidc.okta.issuer-uri", OKTA_ISSUER,
                ROOT + ".providers.oidc.okta.groups-claim", "roles"));
    final var token =
        Map.<String, Object>of(
            "iss", URI.create(OKTA_ISSUER).toURL(),
            "groups", List.of("okta-group"),
            "roles", List.of("okta-role"));

    // when
    final var groups = service.groupIds(query(token));

    // then
    assertThat(groups).containsExactly("okta-role");
  }

  @Test
  void shouldUseDefaultTenantSettingsForUnconfiguredPhysicalTenant() {
    // given
    final var service =
        service(
            Map.of(
                ROOT + ".oidc.groups-claim", "groups",
                TENANT_A_PREFIX + ".oidc.groups-claim", ""));
    final var token = Map.<String, Object>of("groups", List.of("root-group"));

    // when
    final var groups = inPhysicalTenant("tenantz", () -> service.groupIds(query(token)));

    // then
    assertThat(groups).containsExactly("root-group");
  }

  private static Map<String, Object> oktaToken() {
    return Map.of(
        "iss", OKTA_ISSUER, "groups", List.of("okta-group"), "roles", List.of("okta-role"));
  }

  private static <T> T inPhysicalTenant(final String physicalTenantId, final Supplier<T> call) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    return call.get();
  }

  /**
   * Builds the service from configuration properties, with the cluster config resolved the way
   * {@code PhysicalTenantSecurityConfiguration} resolves it when physical tenants are configured.
   */
  private static NoDBMembershipService service(final Map<String, String> properties) {
    final var environment = new MockEnvironment();
    properties.forEach(environment::setProperty);
    final var config = new CamundaSecurityLibraryProperties();
    config.setAuthentication(
        PhysicalTenantAuthConfigurations.forPhysicalTenant("default", environment));
    return new NoDBMembershipService(config, environment);
  }

  private static NoDBMembershipService serviceWithGroupsClaim(final String claim) {
    final var config = new CamundaSecurityLibraryProperties();
    config.getAuthentication().getOidc().setGroupsClaim(claim);
    return new NoDBMembershipService(config, new MockEnvironment());
  }
}
