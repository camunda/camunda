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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.secrets.SecretStoreRegistries;
import io.camunda.application.commons.security.AuthorizationCheckerProvider;
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.ManagementServices;
import io.camunda.service.SecretServices.ResolvedSecret;
import io.camunda.service.SecretServices.SecretErrorCode;
import io.camunda.service.SecretServices.SecretResolutionError;
import io.camunda.service.license.CamundaLicense;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.rebalance.RebalanceRequestSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
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
  void shouldWireDistinctAuthorizationCheckerPerPhysicalTenantIntoSecretServices() {
    // given: SecretServices shares the per-tenant checker wiring with DocumentServices
    final var checkerA = mock(AuthorizationChecker.class);
    final var checkerB = mock(AuthorizationChecker.class);
    when(checkerA.retrieveAuthorizedAuthorizationScopes(any(), any()))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));
    when(checkerB.retrieveAuthorizedAuthorizationScopes(any(), any())).thenReturn(List.of());
    final var registry =
        buildRegistry(
            twoTenants(),
            new AuthorizationCheckerProvider(
                sharedAuthorizationChecker, Map.of(TENANT_A, checkerA, TENANT_B, checkerB)),
            new SecretStoreRegistries(
                Map.of(TENANT_A, registryHolding("token"), TENANT_B, registryHolding("token"))));

    // when / then: tenant A's checker grants REVEAL -- the reference resolves, using only tenant
    // A's checker.
    final var resolvedForA =
        registry
            .secretServices(TENANT_A)
            .resolve(List.of("camunda.secrets.token"), authentication)
            .join();
    assertThat(resolvedForA.resolved())
        .extracting(ResolvedSecret::reference)
        .containsExactly("camunda.secrets.token");
    verify(checkerA).retrieveAuthorizedAuthorizationScopes(any(), any());
    verifyNoInteractions(checkerB);

    // when / then: tenant B's own checker denies REVEAL -- the reference is denied even though
    // tenant A's checker (queried above) would have granted it, and A is not consulted again.
    final var resolvedForB =
        registry
            .secretServices(TENANT_B)
            .resolve(List.of("camunda.secrets.token"), authentication)
            .join();
    assertThat(resolvedForB.resolved()).isEmpty();
    assertThat(resolvedForB.errors())
        .extracting(SecretResolutionError::code)
        .containsExactly(SecretErrorCode.ACCESS_DENIED);
    verify(checkerB).retrieveAuthorizedAuthorizationScopes(any(), any());
    verify(checkerA, times(1)).retrieveAuthorizedAuthorizationScopes(any(), any());
  }

  @Test
  void shouldGateAuthorizationCheckOnPerPhysicalTenantAuthorizationsEnabled() {
    // given: tenant A has authorizations enabled, tenant B has them disabled -- the enable flag
    // must be read from each tenant's own security config, not a single root-scoped one.
    final var tenants = new LinkedHashMap<String, Camunda>();
    tenants.put(TENANT_A, tenantWithAuthorizations(true));
    tenants.put(TENANT_B, tenantWithAuthorizations(false));
    final var checkerA = mock(AuthorizationChecker.class);
    final var checkerB = mock(AuthorizationChecker.class);
    when(checkerA.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));
    final var registry =
        buildRegistry(
            tenants,
            new AuthorizationCheckerProvider(
                sharedAuthorizationChecker, Map.of(TENANT_A, checkerA, TENANT_B, checkerB)));

    // when / then: tenant A enforces the check via its own checker.
    assertThat(
            registry
                .documentServices(TENANT_A)
                .createDocumentBatch(List.of(), authentication)
                .join())
        .isEmpty();
    verify(checkerA).collectPermissionTypes(any(), any(), any());

    // when / then: tenant B has authorizations disabled -- the check is skipped entirely and its
    // checker is never consulted, even though a per-tenant checker exists for it.
    assertThat(
            registry
                .documentServices(TENANT_B)
                .createDocumentBatch(List.of(), authentication)
                .join())
        .isEmpty();
    verifyNoInteractions(checkerB);
  }

  @Test
  void shouldGateExportingAuthorizationCheckOnPerPhysicalTenantAuthorizationsEnabled() {
    // given: same per-tenant enable split as above -- ExportingServices (added in #57842) shares
    // the DocumentServices/SecretServices wiring and must read its enable flag from each tenant's
    // own security config too.
    final var tenants = new LinkedHashMap<String, Camunda>();
    tenants.put(TENANT_A, tenantWithAuthorizations(true));
    tenants.put(TENANT_B, tenantWithAuthorizations(false));
    final var checkerA = mock(AuthorizationChecker.class);
    final var checkerB = mock(AuthorizationChecker.class);
    // tenant A grants no SYSTEM permission -- the enabled check denies and short-circuits to
    // forbidden before the broadcaster is touched.
    when(checkerA.collectPermissionTypes(any(), any(), any())).thenReturn(Collections.emptySet());
    final var registry =
        buildRegistry(
            tenants,
            new AuthorizationCheckerProvider(
                sharedAuthorizationChecker, Map.of(TENANT_A, checkerA, TENANT_B, checkerB)));

    // when / then: tenant A enforces the SYSTEM/UPDATE check via its own checker.
    final var deniedForA = registry.exportingServices(TENANT_A).resumeExporting(authentication);
    assertThat(deniedForA.isCompletedExceptionally()).isTrue();
    verify(checkerA).collectPermissionTypes(any(), any(), any());

    // when / then: tenant B has authorizations disabled -- the check is skipped and its checker is
    // never consulted. (The subsequent broadcast fails on the mock broker client, which is
    // irrelevant to the gate under test.)
    registry.exportingServices(TENANT_B).resumeExporting(authentication);
    verifyNoInteractions(checkerB);
  }

  @Test
  void shouldGateSecretAuthorizationCheckOnPerPhysicalTenantAuthorizationsEnabled() {
    // given: same per-tenant enable split -- SecretServices is the third gated site and reads its
    // enable flag from each tenant's own security config, like DocumentServices/ExportingServices.
    final var tenants = new LinkedHashMap<String, Camunda>();
    tenants.put(TENANT_A, tenantWithAuthorizations(true));
    tenants.put(TENANT_B, tenantWithAuthorizations(false));
    final var checkerA = mock(AuthorizationChecker.class);
    final var checkerB = mock(AuthorizationChecker.class);
    // tenant A grants no SECRET:REVEAL scope -- the enabled check denies the reference via A's
    // own checker.
    when(checkerA.retrieveAuthorizedAuthorizationScopes(any(), any())).thenReturn(List.of());
    final var registry =
        buildRegistry(
            tenants,
            new AuthorizationCheckerProvider(
                sharedAuthorizationChecker, Map.of(TENANT_A, checkerA, TENANT_B, checkerB)));

    // when / then: tenant A enforces the SECRET:REVEAL check via its own checker -> denied.
    final var resolvedForA =
        registry
            .secretServices(TENANT_A)
            .resolve(List.of("camunda.secrets.token"), authentication)
            .join();
    assertThat(resolvedForA.errors())
        .extracting(SecretResolutionError::code)
        .containsExactly(SecretErrorCode.ACCESS_DENIED);
    verify(checkerA).retrieveAuthorizedAuthorizationScopes(any(), any());

    // when / then: tenant B has authorizations disabled -- the check is skipped and its checker is
    // never consulted.
    registry
        .secretServices(TENANT_B)
        .resolve(List.of("camunda.secrets.token"), authentication)
        .join();
    verifyNoInteractions(checkerB);
  }

  @Test
  void shouldWireDistinctSecretStoreRegistryPerPhysicalTenantIntoSecretServices() {
    // given: each physical tenant has its own store registry, keyed by tenant ID
    final var registryA = new SecretStoreRegistry(Map.of());
    final var registryB = new SecretStoreRegistry(Map.of());
    final var registry =
        buildRegistry(
            twoTenants(),
            new AuthorizationCheckerProvider(sharedAuthorizationChecker, Map.of()),
            new SecretStoreRegistries(Map.of(TENANT_A, registryA, TENANT_B, registryB)));

    // then: each tenant's SecretServices holds exactly its own tenant's registry, so the
    // store-backed resolve/list (#58497) can never read another tenant's stores
    assertThat(registry.secretServices(TENANT_A).getSecretStoreRegistry()).isSameAs(registryA);
    assertThat(registry.secretServices(TENANT_B).getSecretStoreRegistry()).isSameAs(registryB);
  }

  @Test
  void shouldFallBackToEmptySecretStoreRegistryWhenTenantAbsentFromRegistries() {
    // given: the registries hold no entry for any tenant (a tenant with no configured stores
    // still gets a noop-store registry from SecretStoreConfiguration, so absence only happens
    // when the secrets bean saw a different tenant set than this wiring)
    final var registry =
        buildRegistry(
            twoTenants(),
            new AuthorizationCheckerProvider(sharedAuthorizationChecker, Map.of()),
            new SecretStoreRegistries(Map.of()));

    // then: SecretServices still receives a (store-less) registry instead of null, mirroring the
    // fallback of SecretStoreRegistries#forPhysicalTenant
    final var fallback = registry.secretServices(TENANT_A).getSecretStoreRegistry();
    assertThat(fallback).isNotNull();
    assertThat(fallback.getStores()).isEmpty();
  }

  @Test
  void shouldWireExactlyOneEntryWhenOnlyTheDefaultPhysicalTenantExists() {
    // given: a single-PT/default-only deployment
    final var defaultChecker = mock(AuthorizationChecker.class);
    when(defaultChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));
    final var registry =
        buildRegistry(
            Map.of("default", tenantWithAuthorizations(true)),
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
  void shouldFailHardWhenAPhysicalTenantIsMissingFromPerTenantCheckers() {
    // given: config drift -- both tenants exist in the resolver, but the provider only has a
    // per-tenant checker for tenantA (tenantB is missing). tenantB must not silently resolve
    // against another tenant's authorization storage.
    final var checkerA = mock(AuthorizationChecker.class);

    // when / then: wiring the registry fails hard for the missing tenant rather than falling back
    // to the shared default checker.
    assertThatThrownBy(
            () ->
                buildRegistry(
                    twoTenants(),
                    new AuthorizationCheckerProvider(
                        sharedAuthorizationChecker, Map.of(TENANT_A, checkerA))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TENANT_B);
  }

  private static Map<String, Camunda> twoTenants() {
    final var tenants = new LinkedHashMap<String, Camunda>();
    tenants.put(TENANT_A, tenantWithAuthorizations(true));
    tenants.put(TENANT_B, tenantWithAuthorizations(true));
    return tenants;
  }

  /** A store registry whose single store holds the given secret names, each with a dummy value. */
  private static SecretStoreRegistry registryHolding(final String... names) {
    final var held = Set.of(names);
    final var store =
        new SecretStore() {
          @Override
          public Map<String, SecretResolutionResult> resolve(final Set<String> requested) {
            return requested.stream()
                .collect(
                    Collectors.toMap(
                        name -> name,
                        name ->
                            held.contains(name)
                                ? new SecretResolutionResult.Resolved("value-of-" + name)
                                : new SecretResolutionResult.Failed(
                                    io.camunda.secretstore.SecretErrorCode.NOT_FOUND,
                                    "unknown",
                                    null)));
          }

          @Override
          public List<String> list() {
            return List.copyOf(held);
          }
        };
    return new SecretStoreRegistry(Map.of("main", store));
  }

  private ServiceRegistry buildRegistry(
      final Map<String, Camunda> tenants,
      final AuthorizationCheckerProvider authorizationCheckerProvider) {
    return buildRegistry(
        tenants, authorizationCheckerProvider, new SecretStoreRegistries(Map.of()));
  }

  private ServiceRegistry buildRegistry(
      final Map<String, Camunda> tenants,
      final AuthorizationCheckerProvider authorizationCheckerProvider,
      final SecretStoreRegistries secretStoreRegistries) {
    final var physicalTenantResolver = mock(PhysicalTenantResolver.class);
    when(physicalTenantResolver.getAll()).thenReturn(tenants);

    return configuration.serviceRegistry(
        physicalTenantResolver,
        mock(BrokerClient.class),
        new SecurityContextProvider(),
        mock(PasswordEncoder.class),
        mock(ActivateJobsHandler.class),
        SearchClientsProxy.noop(),
        authorizationCheckerProvider,
        new GatewayRestConfiguration(),
        mock(BrokerTopologyManager.class),
        mock(ClusterConfigurationManagementRequestSender.class),
        mock(RebalanceRequestSender.class),
        mock(ExportingStateController.class, RETURNS_DEEP_STUBS),
        new SimpleMeterRegistry(),
        new MockEnvironment(),
        new ManagementServices(new CamundaLicense(null)),
        readinessProvider(),
        mock(ObjectProvider.class),
        new ApiServicesExecutorProvider(Executors.newSingleThreadExecutor()),
        secretStoreRegistries);
  }

  /**
   * The readiness bean is taken as an {@link ObjectProvider} so it is resolved only when the
   * cluster-status predicate runs — injecting it eagerly would close a bean cycle through the
   * search schema initializer.
   */
  @SuppressWarnings("unchecked")
  private static ObjectProvider<SecondaryStorageReadiness> readinessProvider() {
    final ObjectProvider<SecondaryStorageReadiness> provider = mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(SecondaryStorageReadiness.ALWAYS_READY);
    return provider;
  }

  /** A physical tenant config with {@code security.authorizations.enabled} set as given. */
  private static Camunda tenantWithAuthorizations(final boolean enabled) {
    final var camunda = new Camunda();
    camunda.getSecurity().getAuthorizations().setEnabled(enabled);
    return camunda;
  }
}
