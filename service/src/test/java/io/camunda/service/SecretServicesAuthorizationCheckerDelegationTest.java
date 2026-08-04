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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.SecretServices.ResolvedSecret;
import io.camunda.service.SecretServices.SecretErrorCode;
import io.camunda.service.SecretServices.SecretResolutionError;
import io.camunda.service.SecretTestSupport.TestSecretStore;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Characterizes {@link SecretServices}'s own contract: every {@code SECRET:REVEAL} check is
 * delegated to whichever {@link AuthorizationChecker} instance it was constructed with, with no
 * shared or static state leaking a check to a sibling instance's checker. Mirrors {@link
 * DocumentServicesAuthorizationCheckerDelegationTest} for the sibling service that shares the same
 * per-physical-tenant checker wiring.
 */
class SecretServicesAuthorizationCheckerDelegationTest {

  private static final String RESOLVABLE_REFERENCE = "camunda.secrets.token";

  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
  private final AuthorizationChecker checkerA = mock(AuthorizationChecker.class);
  private final AuthorizationChecker checkerB = mock(AuthorizationChecker.class);
  private SecretServices servicesA;
  private SecretServices servicesB;

  @BeforeEach
  void beforeEach() {
    final var enabledConfig = new AuthorizationsConfiguration();
    enabledConfig.setEnabled(true);
    servicesA = newSecretServices("tenanta", checkerA, enabledConfig);
    servicesB = newSecretServices("tenantb", checkerB, enabledConfig);
  }

  @Test
  void shouldOnlyQueryOwnPhysicalTenantsCheckerOnResolve() {
    // given: only tenant A's checker grants REVEAL
    when(checkerA.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));

    // when
    final var resolution = servicesA.resolve(List.of(RESOLVABLE_REFERENCE), authentication).join();

    // then: tenant A's own checker authorizes the reveal, and tenant B's is never consulted
    assertThat(resolution.resolved())
        .extracting(ResolvedSecret::reference)
        .containsExactly(RESOLVABLE_REFERENCE);
    assertThat(resolution.errors()).isEmpty();
    verify(checkerA).retrieveAuthorizedAuthorizationScopes(any(), any());
    verifyNoInteractions(checkerB);
  }

  @Test
  void shouldDenyRevealFromWrongTenantGrant() {
    // given: tenant A's checker would grant REVEAL, but this request is scoped to tenant B, whose
    // own checker grants nothing -- proves SecretServices always defers to the checker it was
    // constructed with, never a sibling instance's.
    when(checkerA.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));
    when(checkerB.retrieveAuthorizedAuthorizationScopes(any(), any())).thenReturn(List.of());

    // when
    final var resolution = servicesB.resolve(List.of(RESOLVABLE_REFERENCE), authentication).join();

    // then: tenant B denies the reference and tenant A's grant does not leak across
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors())
        .extracting(SecretResolutionError::code)
        .containsExactly(SecretErrorCode.ACCESS_DENIED);
    verify(checkerB).retrieveAuthorizedAuthorizationScopes(any(), any());
    verify(checkerA, times(0)).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  private SecretServices newSecretServices(
      final String physicalTenantId,
      final AuthorizationChecker authorizationChecker,
      final AuthorizationsConfiguration authorizationsConfig) {
    // a store holding the reference, so an authorized reveal shows up as a resolved value rather
    // than as NOT_FOUND
    final var store = new TestSecretStore().holds("token", "token-value");
    return new SecretServices(
        physicalTenantId,
        mock(BrokerClient.class),
        mock(SecurityContextProvider.class),
        authorizationChecker,
        authorizationsConfig,
        new SecretStoreRegistry(Map.of("main", store)),
        SecretTestSupport.sameThreadExecutorProvider(),
        null);
  }
}
