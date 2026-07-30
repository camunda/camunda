/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.TokenClaimsAuthenticationResolver;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthenticatedAuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CslTenantCheckTest {

  @Mock private TokenClaimsAuthenticationResolver claimsConverter;
  @Mock private AuthenticationConfiguration authConfig;
  @Mock private TypedRecord<?> command;
  @Mock private CamundaAuthentication authentication;

  @Test
  void shouldSkipTenantCheckWhenMultiTenancyDisabled() {
    // given — multi-tenancy checks disabled
    final var tenantCheck = tenantCheck(false);
    final var rejection = new Rejection(RejectionType.FORBIDDEN, "not assigned");

    // when
    final var result = tenantCheck.checkTenant(command, "tenant-a", "ok", rejection);

    // then — no tenant resolution happens; the claims converter is never invoked
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEqualTo("ok");
    verifyNoInteractions(claimsConverter);
  }

  @Test
  void shouldAllowWhenPrincipalIsAssignedToTenant() {
    // given — multi-tenancy on and a principal assigned to tenant-a
    final var tenantCheck = tenantCheck(true);
    when(command.getAuthorizations()).thenReturn(Map.of(Authorization.AUTHORIZED_USERNAME, "user"));
    when(authentication.anonymousUser()).thenReturn(false);
    when(authentication.authenticatedTenantIds()).thenReturn(List.of("tenant-a"));
    when(claimsConverter.resolve(Map.of(Authorization.AUTHORIZED_USERNAME, "user")))
        .thenReturn(authentication);

    // when
    final var result =
        tenantCheck.checkTenant(
            command, "tenant-a", "ok", new Rejection(RejectionType.FORBIDDEN, "denied"));

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEqualTo("ok");
  }

  @Test
  void shouldRejectWhenPrincipalIsNotAssignedToTenant() {
    // given — multi-tenancy on and a principal assigned only to tenant-a
    final var tenantCheck = tenantCheck(true);
    when(command.getAuthorizations()).thenReturn(Map.of(Authorization.AUTHORIZED_USERNAME, "user"));
    when(authentication.anonymousUser()).thenReturn(false);
    when(authentication.authenticatedTenantIds()).thenReturn(List.of("tenant-a"));
    when(claimsConverter.resolve(Map.of(Authorization.AUTHORIZED_USERNAME, "user")))
        .thenReturn(authentication);
    final var rejection = new Rejection(RejectionType.FORBIDDEN, "not assigned to tenant-b");

    // when — a different tenant is requested
    final var result = tenantCheck.checkTenant(command, "tenant-b", "ok", rejection);

    // then — the caller-supplied rejection is returned verbatim
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo(rejection);
  }

  @Test
  void shouldAllowAnonymousUserForAnyTenant() {
    // given — multi-tenancy on but the request is anonymous
    final var tenantCheck = tenantCheck(true);
    when(command.getAuthorizations())
        .thenReturn(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true));

    // when
    final var result =
        tenantCheck.checkTenant(
            command, "tenant-a", "ok", new Rejection(RejectionType.FORBIDDEN, "denied"));

    // then — anonymous access is authorized for every tenant; no claims conversion needed
    assertThat(result.isRight()).isTrue();
    verifyNoInteractions(claimsConverter);
  }

  @Test
  void shouldSkipTenantCheckWhenNoIdentityClaims() {
    // given — multi-tenancy on but the command carries neither a username nor a clientId claim;
    // checkTenant treats this as vacuously authorized regardless of authorizationsEnabled, which it
    // never reads for this check — the skip is deliberate and load-bearing, see checkTenant's
    // javadoc
    final var tenantCheck = tenantCheck(true);
    when(command.getAuthorizations()).thenReturn(Map.of());
    final var rejection = new Rejection(RejectionType.FORBIDDEN, "not assigned");

    // when
    final var result = tenantCheck.checkTenant(command, "tenant-a", "ok", rejection);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEqualTo("ok");
    verifyNoInteractions(claimsConverter);
  }

  @Test
  void shouldSkipTenantsCheckWhenMultiTenancyDisabled() {
    // given — multi-tenancy checks disabled, and a non-anonymous, empty authorized-tenants set
    // that would fail isAuthorizedForTenantIds if the multi-tenancy gate didn't short-circuit
    // first — so a pass here can only be explained by the gate, not by trivial authorization
    final var tenantCheck = tenantCheck(false);
    final var rejection = new Rejection(RejectionType.UNAUTHORIZED, "not authorized");

    // when
    final var result =
        tenantCheck.checkTenantsRequiringPrincipal(
            List.of("tenant-a", "tenant-b"),
            new AuthenticatedAuthorizedTenants(List.of()),
            "ok",
            () -> rejection);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEqualTo("ok");
  }

  @Test
  void shouldAllowWhenPrincipalIsAssignedToAllTenants() {
    // given — a principal pre-resolved as assigned to tenant-a and tenant-b
    final var tenantCheck = tenantCheck(true);
    final var authorizedTenants =
        new AuthenticatedAuthorizedTenants(List.of("tenant-a", "tenant-b"));

    // when
    final var result =
        tenantCheck.checkTenantsRequiringPrincipal(
            List.of("tenant-a", "tenant-b"),
            authorizedTenants,
            "ok",
            () -> new Rejection(RejectionType.UNAUTHORIZED, "denied"));

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEqualTo("ok");
  }

  @Test
  void shouldRejectWhenPrincipalIsNotAssignedToAllTenants() {
    // given — a principal pre-resolved as assigned only to tenant-a
    final var tenantCheck = tenantCheck(true);
    final var authorizedTenants = new AuthenticatedAuthorizedTenants(List.of("tenant-a"));
    final var rejection = new Rejection(RejectionType.UNAUTHORIZED, "not authorized for tenant-b");

    // when — tenant-b is requested in addition to tenant-a
    final var result =
        tenantCheck.checkTenantsRequiringPrincipal(
            List.of("tenant-a", "tenant-b"), authorizedTenants, "ok", () -> rejection);

    // then — the caller-supplied rejection is returned verbatim
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo(rejection);
  }

  @Test
  void shouldAllowAnonymousUserForAnyTenants() {
    // given — the pre-resolved tenants represent an anonymous caller
    final var tenantCheck = tenantCheck(true);

    // when
    final var result =
        tenantCheck.checkTenantsRequiringPrincipal(
            List.of("tenant-a", "tenant-b"),
            AuthorizedTenants.ANONYMOUS,
            "ok",
            () -> new Rejection(RejectionType.UNAUTHORIZED, "denied"));

    // then — anonymous access is authorized for every tenant
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectWhenNoPrincipalAssignedToAnyTenants() {
    // given — a pre-resolved empty-tenant set (what AuthorizedTenantsResolver returns for a
    // no-principal, non-anonymous command when multi-tenancy is enabled); the rejection supplier
    // must be safely callable here since the check failed for a non-anonymous result
    final var tenantCheck = tenantCheck(true);
    final var authorizedTenants = new AuthenticatedAuthorizedTenants(List.of());
    final var rejection = new Rejection(RejectionType.UNAUTHORIZED, "not authorized");

    // when
    final var result =
        tenantCheck.checkTenantsRequiringPrincipal(
            List.of("tenant-a"), authorizedTenants, "ok", () -> rejection);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo(rejection);
  }

  private CslTenantCheck tenantCheck(final boolean multiTenancyChecksEnabled) {
    final var securityConfig =
        new EngineSecurityConfig(
            authConfig,
            /* authorizationsEnabled= */ true,
            multiTenancyChecksEnabled,
            new InitializationConfiguration(),
            EngineSecurityConfigurations.ID_VALIDATION_PATTERN,
            EngineSecurityConfigurations.GROUP_ID_VALIDATION_PATTERN);
    return new CslTenantCheck(claimsConverter, securityConfig);
  }
}
