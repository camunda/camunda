/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.SECRET_READ_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.SECRET_REVEAL_AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.InMemorySecretCache;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.SecretServices.ResolvedSecret;
import io.camunda.service.SecretServices.SecretErrorCode;
import io.camunda.service.SecretTestSupport.TestSecretStore;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SecretServicesTest {

  private static final String PHYSICAL_TENANT_ID = "testtenant";
  private static final String STORE_ID = "main";

  private final AuthorizationChecker authorizationChecker = mock(AuthorizationChecker.class);
  private final AuthorizationsConfiguration authorizationsConfig =
      new AuthorizationsConfiguration();
  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);

  private final TestSecretStore store = new TestSecretStore();
  private final InMemorySecretCache cache = new InMemorySecretCache();
  private final BrokerClient brokerClient = mock(BrokerClient.class);

  private SecretServices services;

  @BeforeEach
  void before() {
    store.holds("token", "token-value");
    store.holds("a", "a-value");
    store.holds("b", "b-value");
    services = newSecretServices(PHYSICAL_TENANT_ID, registryOf(store, cache));
  }

  private static SecretStoreRegistry registryOf(
      final SecretStore store, final InMemorySecretCache cache) {
    return new SecretStoreRegistry(Map.of(STORE_ID, store), Map.of(STORE_ID, cache));
  }

  private SecretServices newSecretServices(final String physicalTenantId) {
    return newSecretServices(physicalTenantId, new SecretStoreRegistry(Map.of()));
  }

  private SecretServices newSecretServices(
      final String physicalTenantId, final SecretStoreRegistry secretStoreRegistry) {
    return new SecretServices(
        physicalTenantId,
        brokerClient,
        mock(SecurityContextProvider.class),
        authorizationChecker,
        authorizationsConfig,
        secretStoreRegistry,
        SecretTestSupport.sameThreadExecutorProvider(),
        null);
  }

  private void grantReveal(final String... references) {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(), eq(SECRET_REVEAL_AUTHORIZATION)))
        .thenReturn(Arrays.stream(references).map(AuthorizationScope::id).toList());
  }

  private void grantRevealWildcard() {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(), eq(SECRET_REVEAL_AUTHORIZATION)))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));
  }

  private void denyAllReveals() {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(), eq(SECRET_REVEAL_AUTHORIZATION)))
        .thenReturn(List.of());
  }

  private void grantRead(final String... references) {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(), eq(SECRET_READ_AUTHORIZATION)))
        .thenReturn(Arrays.stream(references).map(AuthorizationScope::id).toList());
  }

  private void grantReadWildcard() {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(), eq(SECRET_READ_AUTHORIZATION)))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));
  }

  private void denyAllReads() {
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(), eq(SECRET_READ_AUTHORIZATION)))
        .thenReturn(List.of());
  }

  @Test
  void shouldExposeTheSecretStoreRegistryItWasConstructedWith() {
    // given the per-physical-tenant registry handed in by the dist wiring (#58784)
    final var registry = registryOf(store, cache);

    // when
    final var services = newSecretServices(PHYSICAL_TENANT_ID, registry);

    // then the same registry is exposed, so the dist wiring can be asserted on it
    assertThat(services.getSecretStoreRegistry()).isSameAs(registry);
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
    // and the store is asked once, for both distinct names together
    assertThat(store.resolveCalls()).containsExactly(Set.of("a", "b"));
  }

  @Test
  void shouldResolveAuthorizedReferenceFromTheStore() {
    // given authorization passes for the reference and the store holds it
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the store's value is returned
    assertThat(resolution.errors()).isEmpty();
    assertThat(resolution.resolved()).hasSize(1);
    assertThat(resolution.resolved().get(0).reference()).isEqualTo("camunda.secrets.token");
    assertThat(resolution.resolved().get(0).value()).isEqualTo("token-value");
  }

  @Test
  void shouldStripTheReferencePrefixForTheStoreLookup() {
    // given authorization passes for the reference
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");

    // when
    services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the store is asked for the bare secret name it is keyed by, not the full reference
    assertThat(store.resolveCalls()).containsExactly(Set.of("token"));
  }

  @Test
  void shouldResolveFromTheStoreOfItsOwnPhysicalTenant() {
    // given two SecretServices instances, each with its own physical tenant's store holding a
    // different value for the same secret name
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");
    final var otherStore = new TestSecretStore();
    otherStore.holds("token", "other-tenant-token-value");
    final var otherTenantServices =
        newSecretServices("othertenant", registryOf(otherStore, new InMemorySecretCache()));

    // when the same reference is resolved under each tenant
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();
    final var otherResolution =
        otherTenantServices.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then each reads only its own tenant's store, so a cross-tenant read cannot go unnoticed
    assertThat(resolution.resolved().get(0).value()).isEqualTo("token-value");
    assertThat(otherResolution.resolved().get(0).value()).isEqualTo("other-tenant-token-value");
    assertThat(store.resolveCalls()).containsExactly(Set.of("token"));
    assertThat(otherStore.resolveCalls()).containsExactly(Set.of("token"));
  }

  @Test
  void shouldServeCachedReferenceWithoutCallingTheStore() {
    // given the value is already cached for the store
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");
    cache.put("token", "cached-token-value");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the cached value is served and the store is never consulted
    assertThat(resolution.resolved()).hasSize(1);
    assertThat(resolution.resolved().get(0).value()).isEqualTo("cached-token-value");
    assertThat(store.resolveCalls()).isEmpty();
  }

  @Test
  void shouldCacheValueReadFromTheStore() {
    // given authorization passes for the reference
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");

    // when the same reference is resolved twice
    services.resolve(List.of("camunda.secrets.token"), authentication).join();
    final var second = services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the store was read once and the second resolve came from the cache
    assertThat(store.resolveCalls()).containsExactly(Set.of("token"));
    assertThat(second.resolved().get(0).value()).isEqualTo("token-value");
    assertThat(cache.get("token")).contains("token-value");
  }

  @Test
  void shouldOnlyAskTheStoreForUncachedReferences() {
    // given one of two references is cached
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.a", "camunda.secrets.b");
    cache.put("a", "cached-a-value");

    // when both are resolved
    final var resolution =
        services.resolve(List.of("camunda.secrets.a", "camunda.secrets.b"), authentication).join();

    // then only the cache miss reaches the store, and both references are still resolved
    assertThat(store.resolveCalls()).containsExactly(Set.of("b"));
    assertThat(resolution.errors()).isEmpty();
    assertThat(resolution.resolved())
        .extracting(ResolvedSecret::reference, ResolvedSecret::value)
        .containsExactlyInAnyOrder(
            tuple("camunda.secrets.a", "cached-a-value"), tuple("camunda.secrets.b", "b-value"));
  }

  @Test
  void shouldRouteUnknownReferenceToNotFound() {
    // given authorization passes but the store does not hold the secret
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
  void shouldReportReferenceMissingFromTheStoreResponseAsNotFound() {
    // given a store that answers without an entry for the requested name
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");
    store.omitsResults();

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the reference is still described exactly once, as NOT_FOUND, rather than dropped from
    // the response
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReportUnreadableStoreFailureWithoutFailingOthers() {
    // given the store cannot read one of two secrets
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    store.failsResolving(
        "token", io.camunda.secretstore.SecretErrorCode.UNREADABLE, "cannot read the value");

    // when
    final var resolution =
        services
            .resolve(List.of("camunda.secrets.token", "camunda.secrets.a"), authentication)
            .join();

    // then only that reference fails, as UNREADABLE
    assertThat(resolution.resolved())
        .extracting(ResolvedSecret::reference)
        .containsExactly("camunda.secrets.a");
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).reference()).isEqualTo("camunda.secrets.token");
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.UNREADABLE);
  }

  @Test
  void shouldReportAStoreFailureWithoutAskingTheNextStore() {
    // given two stores, the first failing on a reference the second holds. A physical tenant is
    // capped at one configured store today, so this pins what the loop does rather than a case a
    // deployment can reach.
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    final var failing = new TestSecretStore();
    failing.failsResolving(
        "token", io.camunda.secretstore.SecretErrorCode.UNREADABLE, "cannot read the value");
    final var holding = new TestSecretStore().holds("token", "second-store-value");
    final var stores = new LinkedHashMap<String, SecretStore>();
    stores.put("first", failing);
    stores.put("second", holding);

    // when
    final var resolution =
        newSecretServices(PHYSICAL_TENANT_ID, new SecretStoreRegistry(stores))
            .resolve(List.of("camunda.secrets.token"), authentication)
            .join();

    // then the first store's failure is the answer, and the second is never asked
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.UNREADABLE);
    assertThat(holding.resolveCalls()).isEmpty();
  }

  @Test
  void shouldAskTheNextStoreForAReferenceTheFirstDidNotAnswerFor() {
    // given two stores, the first answering without a result for the reference the second holds
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    final var silent = new TestSecretStore();
    silent.omitsResults();
    final var holding = new TestSecretStore().holds("token", "second-store-value");
    final var stores = new LinkedHashMap<String, SecretStore>();
    stores.put("first", silent);
    stores.put("second", holding);

    // when
    final var resolution =
        newSecretServices(PHYSICAL_TENANT_ID, new SecretStoreRegistry(stores))
            .resolve(List.of("camunda.secrets.token"), authentication)
            .join();

    // then the missing entry is not an answer, so the reference reaches the second store
    assertThat(resolution.errors()).isEmpty();
    assertThat(resolution.resolved()).hasSize(1);
    assertThat(resolution.resolved().get(0).value()).isEqualTo("second-store-value");
  }

  @Test
  void shouldReportStoreAccessDenialAsUnreadable() {
    // given the store rejects the cluster's own credentials for the secret
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    store.failsResolving(
        "token", io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED, "denied by the store");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then it is UNREADABLE, not ACCESS_DENIED: this API's ACCESS_DENIED always means the caller
    // lacks SECRET:REVEAL, which is not what a store-side denial says
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.UNREADABLE);
  }

  @Test
  void shouldReportStoreReferenceRejectionAsInvalidReference() {
    // given the store rejects the name as invalid for its own key syntax
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    store.failsResolving(
        "token", io.camunda.secretstore.SecretErrorCode.INVALID_REF, "not a valid secret id");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.INVALID_REFERENCE);
  }

  @Test
  void shouldNotExposeTheStoreFailureMessageInErrors() {
    // given a store failure whose message carries store internals (a path, a secret id)
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    store.failsResolving(
        "token",
        io.camunda.secretstore.SecretErrorCode.UNREADABLE,
        "Failed to read /etc/camunda/secrets/token");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the reported message is the API's own, so the store's layout does not leak to the caller
    assertThat(resolution.errors().get(0).message())
        .doesNotContain("/etc/camunda/secrets")
        .isEqualTo("The configured secret store could not read a value for the reference.");
  }

  @Test
  void shouldNotCacheAFailedResolution() {
    // given the store cannot read the secret
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    store.failsResolving(
        "token", io.camunda.secretstore.SecretErrorCode.UNREADABLE, "cannot read the value");

    // when the reference is resolved twice
    services.resolve(List.of("camunda.secrets.token"), authentication).join();
    services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the store is asked again: a failure must not be cached, or a secret would stay
    // unresolvable until the process restarts
    assertThat(store.resolveCalls()).containsExactly(Set.of("token"), Set.of("token"));
    assertThat(cache.get("token")).isEmpty();
  }

  @Test
  void shouldFailResolveWhenTheStoreCannotBeRead() {
    // given the store itself is unavailable
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    store.isUnavailable();

    // when
    final var resolve = services.resolve(List.of("camunda.secrets.token"), authentication);

    // then the request fails as unavailable rather than reporting the reference as missing, which a
    // caller could not tell apart from a genuinely empty store
    assertThatThrownBy(resolve::join)
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(error -> ((ServiceException) error).getStatus())
        .isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  void shouldFailListWhenTheStoreCannotBeRead() {
    // given the store itself is unavailable
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();
    store.isUnavailable();

    // when
    final var list = services.list(authentication);

    // then the request fails as unavailable rather than looking like an empty tenant
    assertThatThrownBy(list::join)
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(error -> ((ServiceException) error).getStatus())
        .isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  void shouldReportNotFoundWhenNoStoreIsConfigured() {
    // given a physical tenant without any configured store
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    final var withoutStores = newSecretServices(PHYSICAL_TENANT_ID);

    // when
    final var resolution =
        withoutStores.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then every reference is reported as not found
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldListNothingWhenNoStoreIsConfigured() {
    // given a physical tenant without any configured store
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();
    final var withoutStores = newSecretServices(PHYSICAL_TENANT_ID);

    // when
    final var references = withoutStores.list(authentication).join();

    // then
    assertThat(references).isEmpty();
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
    // and the store is only ever asked for the authorized reference
    assertThat(store.resolveCalls()).containsExactly(Set.of("a"));
  }

  @Test
  void shouldReturnAccessDeniedForDeniedNonExistentReference() {
    // given a reference the store does not hold AND the caller is not authorized for it
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
    // the store is never consulted for a denied reference
    assertThat(store.resolveCalls()).isEmpty();
    verify(authorizationChecker).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  void shouldRejectReferenceNamesWithPatternCharactersAsInvalid() {
    // given a batch that is entirely malformed
    authorizationsConfig.setEnabled(true);

    // when references carry pattern/glob, whitespace or dot characters in the name segment
    final var resolution =
        services
            .resolve(
                List.of(
                    "camunda.secrets.*",
                    "camunda.secrets.a%b",
                    "camunda.secrets.a b",
                    "camunda.secrets.a.b"),
                authentication)
            .join();

    // then all are INVALID_REFERENCE and none is ever authorized (they must not reach the
    // resource-id check or a store lookup) — no authorization query is even issued for the batch
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors())
        .extracting(SecretServices.SecretResolutionError::code)
        .containsOnly(SecretErrorCode.INVALID_REFERENCE);
    assertThat(store.resolveCalls()).isEmpty();
    verify(authorizationChecker, never()).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  void shouldResolveReferenceWithHyphenInName() {
    // given a dashed secret name, which the file, GCP and AWS stores all accept
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    store.holds("db-password", "s3cr3t");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.db-password"), authentication).join();

    // then the dash reaches the store untouched and the value comes back
    assertThat(resolution.errors()).isEmpty();
    assertThat(resolution.resolved())
        .extracting(ResolvedSecret::reference, ResolvedSecret::value)
        .containsExactly(tuple("camunda.secrets.db-password", "s3cr3t"));
    assertThat(store.resolveCalls()).containsExactly(Set.of("db-password"));
  }

  @Test
  void shouldAcceptLeadingAndTrailingHyphenInName() {
    // given names a backing store may legitimately hold: the pattern is not anchored, since
    // anchoring would only move the store-versus-reference mismatch rather than close it
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    store.holds("-lead", "one");
    store.holds("trail-", "two");

    // when
    final var resolution =
        services
            .resolve(List.of("camunda.secrets.-lead", "camunda.secrets.trail-"), authentication)
            .join();

    // then both resolve
    assertThat(resolution.errors()).isEmpty();
    assertThat(resolution.resolved())
        .extracting(ResolvedSecret::reference)
        .containsExactlyInAnyOrder("camunda.secrets.-lead", "camunda.secrets.trail-");
  }

  @Test
  void shouldAuthorizeHyphenatedReferenceByItsFullReferenceString() {
    // given a grant on the prefix before the dash, not on the whole reference
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.db");
    store.holds("db-password", "s3cr3t");

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.db-password"), authentication).join();

    // then the dash is part of the resource id, so the grant does not carry over to it
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors())
        .extracting(SecretServices.SecretResolutionError::code)
        .containsExactly(SecretErrorCode.ACCESS_DENIED);
    assertThat(store.resolveCalls()).isEmpty();
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

    // then the boundary is inclusive: the store is asked and answers NOT_FOUND, rather than the
    // reference being rejected as INVALID_REFERENCE
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
    assertThat(store.resolveCalls()).hasSize(1);
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
  void shouldNotListByBareSecretNameAlone() {
    // given the checker grants only the bare name "a", not the canonical camunda.secrets.a
    // reference
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(), eq(SECRET_READ_AUTHORIZATION)))
        .thenReturn(List.of(AuthorizationScope.id("a")));

    // when
    final var references = services.list(authentication).join();

    // then nothing is listed: the authorization resource id is the full camunda.secrets.<name>
    // reference, so a grant on the bare name alone does not match
    assertThat(references).isEmpty();
  }

  @Test
  void shouldNotEnumerateTheStoreWhenCallerHasNoReadGrant() {
    // given the caller holds no SECRET:READ grant at all (no wildcard, no ID scope)
    authorizationsConfig.setEnabled(true);
    denyAllReads();

    // when
    final var references = services.list(authentication).join();

    // then nothing is listed and the store is never enumerated: that enumeration is a tenant-wide
    // store call with real cost, so it is skipped for a result that would be filtered away anyway
    assertThat(references).isEmpty();
    assertThat(store.listCalls()).isZero();
  }

  @Test
  void shouldListReferencesFromTheStore() {
    // given a wildcard read grant
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();

    // when
    final var references = services.list(authentication).join();

    // then every name the store holds is listed as a reference, in a stable sorted order
    assertThat(references)
        .containsExactly("camunda.secrets.a", "camunda.secrets.b", "camunda.secrets.token");
  }

  @Test
  void shouldListFromTheStoreOfItsOwnPhysicalTenant() {
    // given another physical tenant whose store holds a different secret
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();
    final var otherStore = new TestSecretStore().holds("othertenantsecret", "value");
    final var otherTenantServices =
        newSecretServices("othertenant", registryOf(otherStore, new InMemorySecretCache()));

    // when each tenant lists its references
    final var references = services.list(authentication).join();
    final var otherReferences = otherTenantServices.list(authentication).join();

    // then neither tenant sees the other's secrets
    assertThat(references).doesNotContain("camunda.secrets.othertenantsecret");
    assertThat(otherReferences).containsExactly("camunda.secrets.othertenantsecret");
    assertThat(store.listCalls()).isOne();
    assertThat(otherStore.listCalls()).isOne();
  }

  @Test
  void shouldOmitStoreNamesThatCannotFormAReference() {
    // given the store holds names outside the reference syntax, as a file or AWS store may
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();
    store.holds("tls.crt", "certificate");
    store.holds("a b", "spaced");
    store.holds("a*b", "globbed");
    store.holds("ok_name", "value");

    // when
    final var references = services.list(authentication).join();

    // then only the names that form a valid reference are offered: the others could neither be
    // resolved nor written in a BPMN expression, so listing them would only mislead the caller
    assertThat(references).contains("camunda.secrets.ok_name");
    assertThat(references)
        .doesNotContain("camunda.secrets.tls.crt", "camunda.secrets.a b", "camunda.secrets.a*b");
  }

  @Test
  void shouldListStoreNamesContainingAHyphen() {
    // given a dashed name, which every backing store holds as a matter of course
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();
    store.holds("db-password", "password");

    // when
    final var references = services.list(authentication).join();

    // then it is offered, since resolve() takes it and it can be written in a BPMN expression as
    // camunda.secrets.`db-password`
    assertThat(references).contains("camunda.secrets.db-password");
  }

  @Test
  void shouldOmitANullNameFromTheListingWithoutFailingIt() {
    // given a store that lists a null name, which the store SPI's nullness contract forbids but a
    // third-party store could still do
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();
    store.alsoLists(null);

    // when
    final var references = services.list(authentication).join();

    // then the listing costs that one name rather than failing as an internal error
    assertThat(references)
        .containsExactly("camunda.secrets.a", "camunda.secrets.b", "camunda.secrets.token");
  }

  @Test
  void shouldOmitStoreNameThatWouldExceedTheReferenceLengthCap() {
    // given a name whose reference would be one character over the cap resolve() accepts
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();
    final var tooLong = referenceOfLength(SecretServices.MAX_REFERENCE_LENGTH + 1);
    store.holds(tooLong.substring("camunda.secrets.".length()), "value");

    // when
    final var references = services.list(authentication).join();

    // then it is omitted, so the listing only ever offers references resolve() accepts
    assertThat(references).doesNotContain(tooLong);
  }

  @Test
  void shouldNotListCachedNamesThatTheStoreDoesNotHold() {
    // given a value that is cached but no longer held by the store
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();
    cache.put("ghost", "cached-value");

    // when
    final var references = services.list(authentication).join();

    // then the listing polls the store only: the cache holds the values read so far, which is not
    // the tenant's set of secrets
    assertThat(references).doesNotContain("camunda.secrets.ghost");
    assertThat(store.listCalls()).isOne();
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
  void shouldNotAuthorizeByBareSecretNameAlone() {
    // given the checker grants only the bare name "token", not the canonical camunda.secrets.token
    // reference
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(), eq(SECRET_REVEAL_AUTHORIZATION)))
        .thenReturn(List.of(AuthorizationScope.id("token")));

    // when the full reference for that name is resolved
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then it is denied: the authorization resource id is the full camunda.secrets.<name>
    // reference, so a grant on the bare name alone does not match
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
  void shouldNotSendAnyBrokerRequestWhenResolvingOrListing() {
    // given a caller authorized for everything
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    grantReadWildcard();

    // when both endpoints are served
    services.resolve(List.of("camunda.secrets.token"), authentication).join();
    services.list(authentication).join();

    // then neither goes to the broker: a resolved value never enters a command, so it cannot reach
    // the log stream, engine state or an exported record
    verifyNoInteractions(brokerClient);
  }

  @Test
  void shouldNotExposeSecretValuesInErrorEntries() {
    // given a denied reference (no value should ever be produced for it)
    authorizationsConfig.setEnabled(true);
    denyAllReveals();

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then values only ever live in resolved entries; error entries carry metadata only
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors().get(0).message()).doesNotContain("token-value");
  }

  @Test
  void shouldMaskTheResolvedValueInToString() {
    // given a resolved reference
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.token");
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // when the outcome is rendered, as a log statement or a test failure would render it
    final var rendered = resolution.toString();

    // then the value is masked, so printing the outcome anywhere cannot leak the secret. Mirrors
    // the store SPI's SecretResolutionResult.Resolved, which masks for the same reason.
    assertThat(rendered).doesNotContain("token-value").contains("***");
    assertThat(rendered).contains("camunda.secrets.token");
  }

  @Test
  void shouldReturnOutcomeListsThatCannotBeModified() {
    // given a resolved and a failed reference, so both lists are non-empty
    authorizationsConfig.setEnabled(true);
    grantRevealWildcard();
    final var resolution =
        services
            .resolve(List.of("camunda.secrets.token", "camunda.secrets.unknown"), authentication)
            .join();

    // when / then the caller cannot mutate the outcomes the service built
    assertThatThrownBy(() -> resolution.resolved().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> resolution.errors().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldListOnlyAuthorizedReferences() {
    // given the caller is granted READ on one of the three references the store holds
    authorizationsConfig.setEnabled(true);
    grantRead("camunda.secrets.a");

    // when
    final var references = services.list(authentication).join();

    // then only the authorized reference is listed, via a single bulk authorization query
    assertThat(references).containsExactly("camunda.secrets.a");
    verify(authorizationChecker, times(1))
        .retrieveAuthorizedAuthorizationScopes(any(), eq(SECRET_READ_AUTHORIZATION));
  }

  @Test
  void shouldListAllKnownReferencesWithReadWildcardGrant() {
    // given a SECRET:READ:* wildcard grant
    authorizationsConfig.setEnabled(true);
    grantReadWildcard();

    // when
    final var references = services.list(authentication).join();

    // then every reference the store holds is listed, in a stable sorted order
    assertThat(references)
        .containsExactly("camunda.secrets.a", "camunda.secrets.b", "camunda.secrets.token");
  }

  @Test
  void shouldReturnEmptyListWhenNoReadGrant() {
    // given the caller holds no SECRET:READ grant
    authorizationsConfig.setEnabled(true);
    denyAllReads();

    // when
    final var references = services.list(authentication).join();

    // then nothing is listed, even though the store holds references
    assertThat(references).isEmpty();
  }

  @Test
  void shouldListAllReferencesWhenAuthorizationIsDisabled() {
    // given authorization is disabled cluster-wide. Matches DocumentServices#hasDocumentPermission:
    // a deny-all here would make the endpoint unusable in authorization-disabled setups (e.g.
    // C8Run's default), which the epic this endpoint serves explicitly targets.
    authorizationsConfig.setEnabled(false);

    // when
    final var references = services.list(authentication).join();

    // then every reference the store holds is listed and the checker (which cannot be trusted while
    // disabled) is never consulted
    assertThat(references)
        .containsExactly("camunda.secrets.a", "camunda.secrets.b", "camunda.secrets.token");
    verify(authorizationChecker, never())
        .retrieveAuthorizedAuthorizationScopes(any(), eq(SECRET_READ_AUTHORIZATION));
  }

  @Test
  void shouldNotAuthorizeListingWithRevealOnlyGrant() {
    // given the caller holds SECRET:REVEAL on every reference the store holds but no SECRET:READ
    authorizationsConfig.setEnabled(true);
    grantReveal("camunda.secrets.a", "camunda.secrets.b", "camunda.secrets.token");
    denyAllReads();

    // when
    final var references = services.list(authentication).join();

    // then nothing is listed: a REVEAL grant does not imply READ
    assertThat(references).isEmpty();
  }

  @Test
  void shouldNotAuthorizeRevealWithReadOnlyGrant() {
    // given the caller holds SECRET:READ on a reference but no SECRET:REVEAL
    authorizationsConfig.setEnabled(true);
    grantRead("camunda.secrets.token");
    denyAllReveals();

    // when
    final var resolution =
        services.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then the reveal is denied: a READ grant does not imply REVEAL
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }
}
