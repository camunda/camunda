/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.SecretServices.ResolvedSecret;
import io.camunda.service.SecretServices.SecretErrorCode;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SecretServicesTest {

  private static final String PHYSICAL_TENANT_ID = "testtenant";

  private final AuthorizationChecker authorizationChecker = mock(AuthorizationChecker.class);
  private final AuthorizationsConfiguration authorizationsConfig =
      new AuthorizationsConfiguration();
  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);

  private SecretServices services;

  @BeforeEach
  void before() {
    services = newSecretServices(PHYSICAL_TENANT_ID);
  }

  private SecretServices newSecretServices(final String physicalTenantId) {
    return new SecretServices(
        physicalTenantId,
        mock(BrokerClient.class),
        mock(SecurityContextProvider.class),
        authorizationChecker,
        authorizationsConfig,
        mock(ApiServicesExecutorProvider.class),
        null);
  }

  private void grantReveal(final String... references) {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(Arrays.stream(references).map(AuthorizationScope::id).toList());
  }

  private void grantRevealWildcard() {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));
  }

  private void denyAllReveals() {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(List.of());
  }

  @Test
  void shouldDeduplicateReferences() {
    // given authorization passes for every reference
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.a", "camunda.secrets.b");

    // when the same reference appears multiple times
    final var resolution =
        services
            .resolve(
                List.of("camunda.secrets.a", "camunda.secrets.a", "camunda.secrets.b"),
                authentication)
            .join();

    // then it is resolved exactly once
    assertThat(resolution.resolved())
        .extracting(ResolvedSecret::reference)
        .containsExactly("camunda.secrets.a", "camunda.secrets.b");
    assertThat(resolution.errors()).isEmpty();
    // and the authorization query runs once for the whole batch, not once per distinct reference
    verify(authorizationChecker, times(1)).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  void shouldResolveAuthorizedReference() {
    // given authorization passes for the reference
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the (mocked) value is returned, scoped to this tenant
    assertThat(resolution.errors()).isEmpty();
    assertThat(resolution.resolved()).hasSize(1);
    assertThat(resolution.resolved().get(0).reference()).isEqualTo("camunda.secrets.token");
    assertThat(resolution.resolved().get(0).value())
        .isEqualTo("mock-value-for-token-in-tenant-" + PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldScopeMockedValuesByPhysicalTenant() {
    // given two SecretServices instances scoped to different physical tenants, both authorized
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");
    final var otherTenantServices = newSecretServices("othertenant");

    // when the same reference is resolved under each tenant
    final var value =
        services
            .resolve(List.of("camunda.secrets.token"), authentication)
            .join()
            .resolved()
            .get(0)
            .value();
    final var otherValue =
        otherTenantServices
            .resolve(List.of("camunda.secrets.token"), authentication)
            .join()
            .resolved()
            .get(0)
            .value();

    // then the resolved values differ per tenant — the mock never returns an identical value
    // across tenants, which would otherwise mask a cross-tenant leak
    assertThat(value).isNotEqualTo(otherValue);
  }

  @Test
  void shouldRouteUnknownReferenceToNotFound() {
    // given authorization passes but the reference is not one the mock backend knows
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.unknown");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.unknown"), authentication).join();

    // then it is reported as NOT_FOUND rather than resolved to a fabricated value
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).reference()).isEqualTo("camunda.secrets.unknown");
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldRouteDeniedReferenceToErrorsWithoutFailingOthers() {
    // given authorization passes for one reference and is denied for another
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.a");

    // when
    final var resolution =
        services
            .resolve(List.of("camunda.secrets.a", "camunda.secrets.denied"), authentication)
            .join();

    // then the denied reference is an error while the other still resolves
    assertThat(resolution.resolved())
        .extracting(ResolvedSecret::reference)
        .containsExactly("camunda.secrets.a");
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).reference()).isEqualTo("camunda.secrets.denied");
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldReturnAccessDeniedForDeniedNonExistentReference() {
    // given a reference the mock backend does not know AND the caller is not authorized for it
    authorizationsConfig.setEnabled(true);
    denyAllReveals();

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.doesnotexist"), authentication).join();

    // then it is ACCESS_DENIED, never NOT_FOUND — authorization is checked before any lookup, so a
    // denied caller can never distinguish a missing secret from one they simply cannot reveal (no
    // existence oracle)
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
    // the lookup is never consulted for a denied reference
    verify(authorizationChecker).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  void shouldRejectReferenceNamesWithPatternCharactersAsInvalid() {
    // given a batch that is entirely malformed
    authorizationsConfig.setEnabled(true);

    // when references carry pattern/glob or whitespace characters in the name segment
    final var resolution =
        services
            .resolve(
                List.of("camunda.secrets.*", "camunda.secrets.a%b", "camunda.secrets.a b"),
                authentication)
            .join();

    // then all are INVALID_REFERENCE and none is ever authorized (they must not reach the
    // resource-id
    // check or, later, a store lookup) — no authorization query is even issued for the batch
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors())
        .extracting(SecretServices.SecretResolutionError::code)
        .containsOnly(SecretErrorCode.INVALID_REFERENCE);
    verify(authorizationChecker, never()).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  void shouldRejectReferenceExceedingMaxLengthAsInvalid() {
    // given authorization would otherwise pass for every reference
    authorizationsConfig.setEnabled(true);

    // when a reference is one character longer than MAX_REFERENCE_LENGTH
    final var reference = referenceOfLength(SecretServices.MAX_REFERENCE_LENGTH + 1);
    final var resolution = services.resolve(List.of(reference), authentication).join();

    // then it is INVALID_REFERENCE, consistent with every other malformed reference, and no
    // authorization query is issued
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.INVALID_REFERENCE);
    verify(authorizationChecker, never()).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  void shouldAcceptReferenceAtMaxLength() {
    // given authorization passes and the reference is exactly at MAX_REFERENCE_LENGTH
    authorizationsConfig.setEnabled(true);
    final var reference = referenceOfLength(SecretServices.MAX_REFERENCE_LENGTH);
    grantReveal(reference);

    // when
    final var resolution = services.resolve(List.of(reference), authentication).join();

    // then the boundary is inclusive: NOT_FOUND (mock backend doesn't know it), not
    // INVALID_REFERENCE
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  private static String referenceOfLength(final int totalLength) {
    final var prefix = "camunda.secrets.";
    return prefix + "a".repeat(totalLength - prefix.length());
  }

  @Test
  void shouldRouteMalformedReferenceToErrorsBeforeAuthorization() {
    // given a batch that is entirely malformed
    authorizationsConfig.setEnabled(true);

    // when a malformed reference is included
    final var resolution =
        services.resolve(List.of("not.a.secret", "camunda.secrets.a.b"), authentication).join();

    // then both are INVALID_REFERENCE and no authorization query is issued for the batch
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors())
        .extracting(SecretServices.SecretResolutionError::code)
        .containsOnly(SecretErrorCode.INVALID_REFERENCE);
    verify(authorizationChecker, never()).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  void shouldAuthorizeExactlyTheGrantedResourceId() {
    // given the caller is granted REVEAL only on a different reference
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.other");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the ungranted reference is denied — matching is by exact resource id, not a blanket
    // allow once any grant exists
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldAuthorizeAnyReferenceWithWildcardGrant() {
    // given a SECRET:REVEAL:* wildcard grant
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();

    // when resolving references the caller was never explicitly granted
    final var resolution =
        services
            .resolve(List.of("camunda.secrets.token", "camunda.secrets.a"), authentication)
            .join();

    // then the wildcard authorizes every reference in the batch
    assertThat(resolution.errors()).isEmpty();
    assertThat(resolution.resolved())
        .extracting(ResolvedSecret::reference)
        .containsExactlyInAnyOrder("camunda.secrets.token", "camunda.secrets.a");
  }

  @Test
  void shouldAllowRevealWhenAuthorizationIsDisabled() {
    // given authorization is disabled cluster-wide. Matches DocumentServices#hasDocumentPermission:
    // a deny-all here would make the endpoint unusable in authorization-disabled setups (e.g.
    // C8Run's default), which the epic this endpoint serves explicitly targets.
    authorizationsConfig.setEnabled(false);

    // when
    final var resolution = services.resolve(List.of("camunda.secrets.a"), authentication).join();

    // then the reference is resolved and the checker (which cannot be trusted while disabled) is
    // never consulted
    assertThat(resolution.errors()).isEmpty();
    assertThat(resolution.resolved()).hasSize(1);
    assertThat(resolution.resolved().get(0).reference()).isEqualTo("camunda.secrets.a");
    verify(authorizationChecker, never()).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  // Covers the response-body facet of the no-leak AC only. The other facets (no secret value in
  // logs, exported records, or persistence) are not observable while the backend is mocked and are
  // deferred to the store wiring (#56561/#57199).
  void shouldNotExposeSecretValuesInErrorEntries() {
    // given a denied reference (no value should ever be produced for it)
    authorizationsConfig.setEnabled(true);
    denyAllReveals();

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.denied"), authentication).join();

    // then values only ever live in resolved entries; error entries carry metadata only
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors().get(0).message()).doesNotContain("mock-value-for-");
  }
}
