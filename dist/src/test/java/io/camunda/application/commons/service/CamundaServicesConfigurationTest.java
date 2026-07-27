/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.security.AuthorizationCheckerProvider;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.ManagementServices;
import io.camunda.service.license.CamundaLicense;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

class CamundaServicesConfigurationTest {

  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";

  private final CamundaServicesConfiguration configuration = new CamundaServicesConfiguration();
  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
  private final AuthorizationChecker sharedAuthorizationChecker = mock(AuthorizationChecker.class);

  @Test
  void shouldWireDistinctAuthorizationCheckerPerPhysicalTenantIntoDocumentServices() {
    // given
    final var checkerA = mock(AuthorizationChecker.class);
    final var checkerB = mock(AuthorizationChecker.class);
    when(checkerA.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));
    when(checkerB.collectPermissionTypes(any(), any(), any())).thenReturn(Collections.emptySet());
    final var registry =
        buildRegistry(
            twoTenants(),
            new AuthorizationCheckerProvider(
                sharedAuthorizationChecker, Map.of(TENANT_A, checkerA, TENANT_B, checkerB)));

    // when / then: tenant A's checker grants CREATE -- the batch (empty, so no document store is
    // touched) completes normally, using only tenant A's checker.
    assertThat(
            registry
                .documentServices(TENANT_A)
                .createDocumentBatch(List.of(), authentication)
                .join())
        .isEmpty();
    verify(checkerA).collectPermissionTypes(any(), any(), any());
    verifyNoInteractions(checkerB);

    // when / then: tenant B's own checker denies CREATE -- must fail even though tenant A's
    // checker (queried above) would have granted it.
    final var deniedFuture =
        registry.documentServices(TENANT_B).createDocumentBatch(List.of(), authentication);
    assertThat(deniedFuture.isCompletedExceptionally()).isTrue();
    verify(checkerB).collectPermissionTypes(any(), any(), any());
    // re-verify checkerA's count is still exactly one -- catches a leak in the other direction,
    // where querying tenant B would incorrectly also consult tenant A's checker.
    verify(checkerA, times(1)).collectPermissionTypes(any(), any(), any());
  }

  @Test
  void shouldWireExactlyOneEntryWhenOnlyTheDefaultPhysicalTenantExists() {
    // given: a single-PT/default-only deployment
    final var defaultChecker = mock(AuthorizationChecker.class);
    when(defaultChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));
    final var registry =
        buildRegistry(
            Map.of("default", new Camunda()),
            new AuthorizationCheckerProvider(
                sharedAuthorizationChecker, Map.of("default", defaultChecker)));

    // when
    final var future =
        registry.documentServices("default").createDocumentBatch(List.of(), authentication);

    // then
    assertThat(future.join()).isEmpty();
    verify(defaultChecker).collectPermissionTypes(any(), any(), any());
    assertThatThrownBy(() -> registry.documentServices("some-other-tenant"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFallBackToSharedAuthorizationCheckerWhenNoPerTenantCheckersExist() {
    // given: SecondaryStorageType.none -- no PhysicalTenantSearchClientReaders bean exists, so the
    // provider has no per-tenant checkers and every tenant falls back to the shared,
    // default-tenant-pinned checker. That is correct here, since there is exactly one authorization
    // source cluster-wide in this mode.
    when(sharedAuthorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));
    final var registry =
        buildRegistry(
            twoTenants(), new AuthorizationCheckerProvider(sharedAuthorizationChecker, Map.of()));

    // when / then: must not NPE, and both tenants fall back to the same shared checker.
    assertThat(
            registry
                .documentServices(TENANT_A)
                .createDocumentBatch(List.of(), authentication)
                .join())
        .isEmpty();
    assertThat(
            registry
                .documentServices(TENANT_B)
                .createDocumentBatch(List.of(), authentication)
                .join())
        .isEmpty();
    verify(sharedAuthorizationChecker, times(2)).collectPermissionTypes(any(), any(), any());
  }

  @Test
  void shouldFallBackToSharedAuthorizationCheckerForTenantMissingFromPerTenantCheckers() {
    // given: the provider only has a per-tenant checker for tenantA -- tenantB is missing, e.g.
    // config drift between PhysicalTenantResolver and the per-tenant checkers.
    final var checkerA = mock(AuthorizationChecker.class);
    when(checkerA.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));
    when(sharedAuthorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));
    final var registry =
        buildRegistry(
            twoTenants(),
            new AuthorizationCheckerProvider(
                sharedAuthorizationChecker, Map.of(TENANT_A, checkerA)));

    // when / then: tenantA uses its own checker, present in the map.
    assertThat(
            registry
                .documentServices(TENANT_A)
                .createDocumentBatch(List.of(), authentication)
                .join())
        .isEmpty();
    verify(checkerA).collectPermissionTypes(any(), any(), any());
    verifyNoInteractions(sharedAuthorizationChecker);

    // when / then: tenantB, missing from the map, falls back to the shared checker without
    // NPE-ing, instead of failing the whole tenant.
    assertThat(
            registry
                .documentServices(TENANT_B)
                .createDocumentBatch(List.of(), authentication)
                .join())
        .isEmpty();
    verify(sharedAuthorizationChecker).collectPermissionTypes(any(), any(), any());
  }

  private static Map<String, Camunda> twoTenants() {
    final var tenants = new LinkedHashMap<String, Camunda>();
    tenants.put(TENANT_A, new Camunda());
    tenants.put(TENANT_B, new Camunda());
    return tenants;
  }

  private ServiceRegistry buildRegistry(
      final Map<String, Camunda> tenants,
      final AuthorizationCheckerProvider authorizationCheckerProvider) {
    final var physicalTenantResolver = mock(PhysicalTenantResolver.class);
    when(physicalTenantResolver.getAll()).thenReturn(tenants);

    final var cslProperties = new CamundaSecurityLibraryProperties();
    cslProperties.getAuthorizations().setEnabled(true);

    return configuration.serviceRegistry(
        physicalTenantResolver,
        mock(BrokerClient.class),
        new SecurityContextProvider(),
        mock(PasswordEncoder.class),
        mock(ActivateJobsHandler.class),
        SearchClientsProxy.noop(),
        authorizationCheckerProvider,
        cslProperties,
        new GatewayRestConfiguration(),
        mock(BrokerTopologyManager.class),
        new SimpleMeterRegistry(),
        new MockEnvironment(),
        new ManagementServices(new CamundaLicense(null)),
        new ApiServicesExecutorProvider(Executors.newSingleThreadExecutor()));
  }
}
