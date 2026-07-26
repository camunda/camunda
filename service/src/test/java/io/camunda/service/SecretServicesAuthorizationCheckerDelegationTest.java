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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.SecretServices.SecretErrorCode;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Characterizes {@link SecretServices}'s own contract: every permission check is delegated to
 * whichever {@link AuthorizationChecker} instance it was constructed with, with no shared or static
 * state leaking a check to a sibling instance's checker.
 *
 * <p>{@code SecretServices} has the identical shared-checker wiring bug shape as {@code
 * DocumentServices} (camunda#58441) -- see {@code
 * DocumentServicesAuthorizationCheckerDelegationTest} for the equivalent documents-side coverage.
 * That wiring bug lived in {@code CamundaServicesConfiguration}, not in this class, which was
 * always correct -- see {@code CamundaServicesConfigurationTest} for the regression guard on the
 * actual fix.
 */
class SecretServicesAuthorizationCheckerDelegationTest {

  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
  private final AuthorizationChecker checkerA = mock(AuthorizationChecker.class);
  private final AuthorizationChecker checkerB = mock(AuthorizationChecker.class);
  private SecretServices servicesA;
  private SecretServices servicesB;

  @BeforeEach
  void beforeEach() {
    final var enabledConfig = new AuthorizationsConfiguration();
    enabledConfig.setEnabled(true);
    servicesA =
        new SecretServices(
            "tenant-a",
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            checkerA,
            enabledConfig,
            mock(ApiServicesExecutorProvider.class),
            null);
    servicesB =
        new SecretServices(
            "tenant-b",
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            checkerB,
            enabledConfig,
            mock(ApiServicesExecutorProvider.class),
            null);
  }

  @Test
  void shouldOnlyQueryOwnPhysicalTenantsCheckerOnResolve() {
    // given
    when(checkerA.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));

    // when
    final var resolution =
        servicesA.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then
    assertThat(resolution.resolved()).hasSize(1);
    assertThat(resolution.errors()).isEmpty();
    verify(checkerA).retrieveAuthorizedAuthorizationScopes(any(), any());
    verifyNoInteractions(checkerB);
  }

  @Test
  void shouldDenyResolveFromWrongTenantGrant() {
    // given: tenant A's checker would grant a wildcard, but this request is scoped to tenant B,
    // whose own checker grants nothing -- proves SecretServices always defers to the checker it
    // was constructed with, never a sibling instance's.
    when(checkerA.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));
    when(checkerB.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(Collections.emptyList());

    // when
    final var resolution =
        servicesB.resolve(List.of("camunda.secrets.token"), authentication).join();

    // then
    assertThat(resolution.resolved()).isEmpty();
    assertThat(resolution.errors()).hasSize(1);
    assertThat(resolution.errors().get(0).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
    verify(checkerB).retrieveAuthorizedAuthorizationScopes(any(), any());
    verifyNoInteractions(checkerA);
  }
}
