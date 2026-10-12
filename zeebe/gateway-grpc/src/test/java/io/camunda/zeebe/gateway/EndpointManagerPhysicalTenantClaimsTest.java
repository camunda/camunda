/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.gateway.api.job.CompleteJobStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.grpc.Context;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the broker request claims of a gRPC call are built with the security config of the
 * physical tenant the call targets, not with the {@code default} tenant's.
 */
final class EndpointManagerPhysicalTenantClaimsTest {

  private static final String TENANT = "tenanta";

  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();

  @Test
  void shouldNotFailWhenOnlyTheDefaultTenantConfiguresAGroupsClaim() {
    // given groups are claim-based on default but not on the tenant, so the handler sets none
    final var endpointManager = endpointManager(oidcConfig("groups", true), oidcConfig(null, true));
    final ServerStreamObserver<CompleteJobResponse> observer = observer();

    // when
    completeJob(endpointManager, observer, null, Map.of("sub", "alice"));

    // then
    verify(observer, never()).onError(any());
    assertThat(sentClaims())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, "alice")
        .doesNotContainKey(Authorization.USER_GROUPS_CLAIMS);
  }

  @Test
  void shouldForwardGroupsWhenOnlyTheTenantConfiguresAGroupsClaim() {
    // given
    final var endpointManager = endpointManager(oidcConfig(null, true), oidcConfig("groups", true));

    // when
    completeJob(endpointManager, observer(), List.of("team"), Map.of("sub", "alice"));

    // then
    assertThat(sentClaims()).containsEntry(Authorization.USER_GROUPS_CLAIMS, List.of("team"));
  }

  @Test
  void shouldForwardTokenClaimsWhenOnlyTheTenantEnablesAuthorizations() {
    // given
    final var endpointManager = endpointManager(oidcConfig(null, false), oidcConfig(null, true));

    // when
    completeJob(endpointManager, observer(), null, Map.of("sub", "alice"));

    // then
    assertThat(sentClaims()).containsEntry(Authorization.USER_TOKEN_CLAIMS, Map.of("sub", "alice"));
  }

  @Test
  void shouldRejectRequestForTenantWithoutSecurityConfig() {
    // given
    final var endpointManager = endpointManager(oidcConfig(null, true), oidcConfig(null, true));
    final ServerStreamObserver<CompleteJobResponse> observer = observer();

    // when
    completeJob(endpointManager, observer, "unknown", null, Map.of("sub", "alice"));

    // then
    verify(observer).onError(any());
    assertThat(brokerClient.getBrokerRequests()).isEmpty();
  }

  private EndpointManager endpointManager(
      final EngineSecurityConfig defaultConfig, final EngineSecurityConfig tenantConfig) {
    new CompleteJobStub().registerWith(brokerClient);
    return new EndpointManager(
        brokerClient,
        null,
        null,
        Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, defaultConfig, TENANT, tenantConfig),
        100);
  }

  private static EngineSecurityConfig oidcConfig(
      final String groupsClaim, final boolean authorizationsEnabled) {
    final var config =
        EngineSecurityConfigurations.defaultConfig()
            .withAuthorizationsEnabled(authorizationsEnabled)
            .withMultiTenancyChecksEnabled(false);
    config.getAuthentication().setMethod(AuthenticationMethod.OIDC);
    config.getAuthentication().getOidc().setGroupsClaim(groupsClaim);
    return config;
  }

  private static void completeJob(
      final EndpointManager endpointManager,
      final ServerStreamObserver<CompleteJobResponse> observer,
      final List<String> groups,
      final Map<String, Object> tokenClaims) {
    completeJob(endpointManager, observer, TENANT, groups, tokenClaims);
  }

  private static void completeJob(
      final EndpointManager endpointManager,
      final ServerStreamObserver<CompleteJobResponse> observer,
      final String physicalTenantId,
      final List<String> groups,
      final Map<String, Object> tokenClaims) {
    Context context =
        Context.current()
            .withValue(InterceptorUtil.getPhysicalTenantIdKey(), physicalTenantId)
            .withValue(AuthenticationHandler.USERNAME, "alice")
            .withValue(AuthenticationHandler.Oidc.USER_CLAIMS, tokenClaims);
    if (groups != null) {
      context = context.withValue(AuthenticationHandler.GROUPS_CLAIMS, groups);
    }
    context.run(
        () ->
            endpointManager.completeJob(
                CompleteJobRequest.newBuilder().setJobKey(1L).build(), observer));
  }

  private Map<String, Object> sentClaims() {
    final BrokerExecuteCommand<?> request = brokerClient.getSingleBrokerRequest();
    return request.getAuthorization().toDecodedMap();
  }

  @SuppressWarnings("unchecked")
  private static ServerStreamObserver<CompleteJobResponse> observer() {
    return mock(ServerStreamObserver.class);
  }
}
