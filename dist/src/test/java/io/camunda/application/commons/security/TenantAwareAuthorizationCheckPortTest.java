/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.security.api.context.TokenClaimsAuthenticationResolver;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.ScopedAuthorizationCheckPortFactory;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantAwareAuthorizationCheckPortTest {

  private static final String COMPONENT_ADMIN = "admin";
  private static final String COMPONENT_IDENTITY_LEGACY_ALIAS = "identity";
  private static final String COMPONENT_OPERATE = "operate";

  private final AuthorizationScopeRepositoryPort scopeRepository =
      mock(AuthorizationScopeRepositoryPort.class);
  private final TokenClaimsAuthenticationResolver claimsResolver =
      mock(TokenClaimsAuthenticationResolver.class);
  private final AuthorizationCheckPort authorizationCheckPort = noPerTenantScopesCheckPort();
  private final CamundaAuthentication authentication =
      CamundaAuthentication.of(b -> b.user("alice"));

  /**
   * A no-per-tenant-scopes port, backed by a single {@code default}-keyed scope. Used by every test
   * below that exercises {@link TenantAwareAuthorizationCheckPort}'s own delegation/alias logic,
   * not the per-tenant resolution itself — see {@link
   * #shouldFailHardWhenPerTenantScopesConfiguredButNoPhysicalTenantResolved} for that.
   */
  private AuthorizationCheckPort noPerTenantScopesCheckPort() {
    final var checkPorts =
        ScopedAuthorizationCheckPortFactory.create(
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, scopeRepository),
            claimsResolver,
            List.of(),
            true,
            false);
    return new TenantAwareAuthorizationCheckPort(checkPorts, false, claimsResolver);
  }

  @Test
  void shouldGrantAdminComponentAccessViaLegacyIdentityAlias() {
    // given: principal lacks the admin COMPONENT/ACCESS grant but holds the legacy identity one
    when(scopeRepository.hasAuthorizedScope(any(), any(), any(), anyList())).thenReturn(false);
    when(scopeRepository.hasAuthorizedScope(
            any(),
            any(),
            any(),
            argThat(ids -> ids != null && ids.contains(COMPONENT_IDENTITY_LEGACY_ALIAS))))
        .thenReturn(true);

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(authentication, componentAccess(COMPONENT_ADMIN));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldDenyAdminComponentAccessWhenNeitherAdminNorIdentityGranted() {
    // given
    when(scopeRepository.hasAuthorizedScope(any(), any(), any(), anyList())).thenReturn(false);

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(authentication, componentAccess(COMPONENT_ADMIN));

    // then: the alias fallback does not manufacture access; the original rejection surfaces
    assertThat(result.isLeft()).isTrue();
    assertThat(result.leftValue())
        .isEqualTo(
            new AuthorizationRejection.Permission(
                AuthorizationResourceType.COMPONENT, PermissionType.ACCESS, COMPONENT_ADMIN));
  }

  @Test
  void shouldNotApplyIdentityAliasToOtherComponents() {
    // given: the alias only maps identity -> admin; other components must not benefit from it
    when(scopeRepository.hasAuthorizedScope(any(), any(), any(), anyList())).thenReturn(false);
    when(scopeRepository.hasAuthorizedScope(
            any(),
            any(),
            any(),
            argThat(ids -> ids != null && ids.contains(COMPONENT_IDENTITY_LEGACY_ALIAS))))
        .thenReturn(true);

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(authentication, componentAccess(COMPONENT_OPERATE));

    // then
    assertThat(result.isLeft()).isTrue();
  }

  @Test
  void shouldPassThroughDirectAdminGrantWithoutConsultingAlias() {
    // given
    when(scopeRepository.hasAuthorizedScope(any(), any(), any(), anyList())).thenReturn(true);

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(authentication, componentAccess(COMPONENT_ADMIN));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldDelegateClaimsBasedCheckThroughResolver() {
    // given
    final Map<String, Object> claims = Map.of("sub", "alice");
    when(claimsResolver.resolve(claims)).thenReturn(authentication);
    when(scopeRepository.hasAuthorizedScope(any(), any(), any(), anyList())).thenReturn(true);

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(claims, componentAccess(COMPONENT_OPERATE));

    // then
    assertThat(result.isRight()).isTrue();
    verify(claimsResolver).resolve(claims);
  }

  @Test
  void shouldDelegateResourceBoundCheckWithoutAliasFallback() {
    // given: an admin-component-shaped requirement declared as property-based
    // (resourcePropertyNames
    // rather than resourceIds), which the identity alias -- an RBAC-only concept -- cannot apply to
    final RequiredAuthorization<String> authorization =
        RequiredAuthorization.<String>of(
                b ->
                    b.resourceType(AuthorizationResourceType.COMPONENT)
                        .permissionType(PermissionType.ACCESS))
            .withResourcePropertyNames(Set.of("owner"));
    when(scopeRepository.findAuthorizedPropertyScopes(any(), any(), any(), any()))
        .thenReturn(List.of());

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(authentication, authorization, "some-resource");

    // then: denied (no matching granted property scope), and the RBAC/alias path was never touched
    assertThat(result.isLeft()).isTrue();
    verify(scopeRepository, never()).hasAuthorizedScope(any(), any(), any(), anyList());
  }

  @Test
  void shouldPreserveOtherResourceIdsWhenApplyingIdentityAlias() {
    // given: a multi-id requirement (admin + operate); only the legacy identity alias is granted
    when(scopeRepository.hasAuthorizedScope(any(), any(), any(), anyList())).thenReturn(false);
    when(scopeRepository.hasAuthorizedScope(
            any(),
            any(),
            any(),
            argThat(ids -> ids != null && ids.contains(COMPONENT_IDENTITY_LEGACY_ALIAS))))
        .thenReturn(true);

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(
            authentication,
            componentAccess(COMPONENT_ADMIN)
                .withResourceIds(List.of(COMPONENT_ADMIN, COMPONENT_OPERATE)));

    // then: the alias must only substitute admin -> identity, not discard operate; since operate
    // is not granted, the overall check must still be denied
    assertThat(result.isLeft()).isTrue();
  }

  @Test
  void shouldGrantAccessViaAliasWhenAllOtherResourceIdsAreAlsoGranted() {
    // given: a multi-id requirement (admin + operate); identity alias and operate are both granted
    when(scopeRepository.hasAuthorizedScope(any(), any(), any(), anyList())).thenReturn(false);
    when(scopeRepository.hasAuthorizedScope(
            any(),
            any(),
            any(),
            argThat(ids -> ids != null && ids.contains(COMPONENT_IDENTITY_LEGACY_ALIAS))))
        .thenReturn(true);
    when(scopeRepository.hasAuthorizedScope(
            any(), any(), any(), argThat(ids -> ids != null && ids.contains(COMPONENT_OPERATE))))
        .thenReturn(true);

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(
            authentication,
            componentAccess(COMPONENT_ADMIN)
                .withResourceIds(List.of(COMPONENT_ADMIN, COMPONENT_OPERATE)));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void
      shouldResolveToTheSingleDefaultScopeRegardlessOfPhysicalTenantContextWhenNoPerTenantScopesConfigured() {
    // given — hasPerTenantScopes=false always resolves the seeded default entry; this test runs
    // outside any request scope or propagated tenant (PhysicalTenantContext.currentOrNull() would
    // return null here), yet the check still succeeds because that context is never consulted
    when(scopeRepository.hasAuthorizedScope(any(), any(), any(), anyList())).thenReturn(true);

    // when
    final Either<AuthorizationRejection, Void> result =
        authorizationCheckPort.check(authentication, componentAccess(COMPONENT_OPERATE));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldFailHardWhenPerTenantScopesConfiguredButNoPhysicalTenantResolved() {
    // given — per-tenant scopes exist (keyed by physical tenant id, not "default"), and this test
    // runs outside any request scope or propagated tenant, so
    // PhysicalTenantContext.currentOrNull() returns null
    final var checkPorts =
        ScopedAuthorizationCheckPortFactory.create(
            Map.of("tenant-a", scopeRepository), claimsResolver, List.of(), true, false);
    final AuthorizationCheckPort perTenantPort =
        new TenantAwareAuthorizationCheckPort(checkPorts, true, claimsResolver);

    // when / then — CSL's fail-hard forScope(null) surfaces rather than silently resolving against
    // tenant-a's storage, which would break tenant isolation for an unstamped request
    assertThatIllegalStateException()
        .isThrownBy(() -> perTenantPort.check(authentication, componentAccess(COMPONENT_ADMIN)));
  }

  private static RequiredAuthorization<Void> componentAccess(final String resourceId) {
    return RequiredAuthorization.<Void>of(
            b ->
                b.resourceType(AuthorizationResourceType.COMPONENT)
                    .permissionType(PermissionType.ACCESS))
        .withResourceId(resourceId);
  }
}
