/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClientImpl;
import io.camunda.zeebe.gateway.interceptors.util.ContextInspectingInterceptor;
import io.camunda.zeebe.gateway.interceptors.util.TestInterceptor;
import io.camunda.zeebe.gateway.interceptors.util.TestTenantProvidingInterceptor;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.util.NetUtil;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@AutoCloseResources
final class InterceptorIT {

  private final GatewayCfg config = new GatewayCfg();
  private final ActorScheduler scheduler =
      ActorScheduler.newActorScheduler()
          .setCpuBoundActorThreadCount(1)
          .setIoBoundActorThreadCount(1)
          .build();

  private AtomixCluster cluster;
  private BrokerClient brokerClient;
  private JobStreamClient jobStreamClient;
  private Gateway gateway;
  private BrokerTopologyManagerImpl topologyManager;
  @AutoCloseResource private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void beforeEach() {
    final var clusterAddress = SocketUtil.getNextAddress();
    final var gatewayAddress = SocketUtil.getNextAddress();
    config
        .getCluster()
        .setHost(clusterAddress.getHostName())
        .setPort(clusterAddress.getPort())
        .setRequestTimeout(Duration.ofSeconds(3));
    config.getNetwork().setHost(gatewayAddress.getHostName()).setPort(gatewayAddress.getPort());
    config.init();

    cluster =
        AtomixCluster.builder(meterRegistry)
            .withAddress(Address.from(clusterAddress.getHostName(), clusterAddress.getPort()))
            .build();
    topologyManager =
        new BrokerTopologyManagerImpl(
            () -> cluster.getMembershipService().getMembers(), BrokerClientTopologyMetrics.NOOP);
    cluster.getMembershipService().addListener(topologyManager);

    brokerClient =
        new BrokerClientImpl(
            config.getCluster().getRequestTimeout(),
            cluster.getMessagingService(),
            cluster.getEventService(),
            scheduler,
            topologyManager,
            BrokerClientRequestMetrics.NOOP);

    jobStreamClient = new JobStreamClientImpl(scheduler, cluster.getCommunicationService());
    gateway =
        new Gateway(
            config, brokerClient, scheduler, jobStreamClient.streamer(), new SimpleMeterRegistry());

    cluster.start().join();
    scheduler.start();
    scheduler.submitActor(topologyManager);
    jobStreamClient.start().join();
    brokerClient.start().forEach(ActorFuture::join);
    cluster.getMembershipService().addListener(topologyManager);
    topologyManager.addTopologyListener(jobStreamClient);

    // gateway is purposefully not started to allow configuration changes
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(
        gateway,
        brokerClient,
        topologyManager,
        jobStreamClient,
        scheduler,
        () -> cluster.stop().join());
  }

  @Test
  void shouldAbortDeploymentCalls() {
    // given
    final var interceptorCfg = new InterceptorCfg();
    interceptorCfg.setId("test");
    interceptorCfg.setClassName(TestInterceptor.class.getName());
    config.getInterceptors().add(interceptorCfg);

    // when
    gateway.start().join();
    try (final var client = createZeebeClient()) {
      final Future<DeploymentEvent> result =
          client
              .newDeployResourceCommand()
              .addResourceFromClasspath("processes/one-task-process.bpmn")
              .send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(StatusRuntimeException.class)
          .withMessage("PERMISSION_DENIED: Aborting because of test");
    }
  }

  @Test
  void shouldInjectQueryApiViaContext() {
    // given
    final var interceptorCfg = new InterceptorCfg();
    interceptorCfg.setId("test");
    interceptorCfg.setClassName(ContextInspectingInterceptor.class.getName());
    config.getInterceptors().add(interceptorCfg);

    // when
    gateway.start().join();
    try (final var client = createZeebeClient()) {
      try {
        client.newTopologyRequest().send().join();
      } catch (final ClientStatusException ignored) {
        // ignore any errors, we just really care that the interceptor was called
      }
    }

    // then
    assertThat(ContextInspectingInterceptor.CONTEXT_QUERY_API.get()).isNotNull();
  }

  @Test
  void shouldSetAuthorizedTenantsViaContext() {
    // given
    final var tenantInterceptorCfg = new InterceptorCfg();
    tenantInterceptorCfg.setId("tenantProviderTest");
    tenantInterceptorCfg.setClassName(TestTenantProvidingInterceptor.class.getName());
    final var contextInspectingInterceptorCfg = new InterceptorCfg();
    contextInspectingInterceptorCfg.setId("test");
    contextInspectingInterceptorCfg.setClassName(ContextInspectingInterceptor.class.getName());

    config.getInterceptors().add(0, tenantInterceptorCfg);
    config.getInterceptors().add(1, contextInspectingInterceptorCfg);

    // when
    gateway.start().join();
    try (final var client = createZeebeClient()) {
      try {
        client.newTopologyRequest().send().join();
      } catch (final ClientStatusException ignored) {
        // ignore any errors, we just really care that the interceptor was called
      }
    }

    // then
    assertThat(ContextInspectingInterceptor.CONTEXT_TENANT_IDS.get()).isNotNull();
    assertThat(ContextInspectingInterceptor.CONTEXT_TENANT_IDS.get())
        .containsExactlyInAnyOrderElementsOf(List.of("tenant-1"));
  }

  @Test
  void shouldUseLastValueOfAuthorizedTenantsSetViaContext() {
    // given
    final var tenantInterceptorCfg = new InterceptorCfg();
    tenantInterceptorCfg.setId("tenantProviderTest");
    tenantInterceptorCfg.setClassName(TestTenantProvidingInterceptor.class.getName());
    final var secondTenantInterceptorCfg = new InterceptorCfg();
    secondTenantInterceptorCfg.setId("secondTenantProviderTest");
    secondTenantInterceptorCfg.setClassName(TestTenantProvidingInterceptor.class.getName());
    final var contextInspectingInterceptorCfg = new InterceptorCfg();
    contextInspectingInterceptorCfg.setId("test");
    contextInspectingInterceptorCfg.setClassName(ContextInspectingInterceptor.class.getName());

    config.getInterceptors().addFirst(tenantInterceptorCfg);
    config.getInterceptors().add(secondTenantInterceptorCfg);
    config.getInterceptors().addLast(contextInspectingInterceptorCfg);

    // reset calls from previous tests
    TestTenantProvidingInterceptor.resetInterceptorsCalls();

    // when
    gateway.start().join();
    try (final var client = createZeebeClient()) {
      try {
        client.newTopologyRequest().send().join();
      } catch (final ClientStatusException ignored) {
        // ignore any errors, we just really care that the interceptor was called
      }
    }

    // then
    assertThat(ContextInspectingInterceptor.CONTEXT_TENANT_IDS.get()).isNotNull();
    assertThat(ContextInspectingInterceptor.CONTEXT_TENANT_IDS.get())
        .containsExactlyInAnyOrderElementsOf(List.of("tenant-2"));
  }

  @Test
  void shouldNotUseAuthorizedTenantsSetViaContextWhenMultiTenancyIsDisabled() {
    // given
    final var tenantInterceptorCfg = new InterceptorCfg();
    tenantInterceptorCfg.setId("tenantProviderTest");
    tenantInterceptorCfg.setClassName(TestTenantProvidingInterceptor.class.getName());
    final var contextInspectingInterceptorCfg = new InterceptorCfg();
    contextInspectingInterceptorCfg.setId("test");
    contextInspectingInterceptorCfg.setClassName(ContextInspectingInterceptor.class.getName());

    config.getInterceptors().addFirst(tenantInterceptorCfg);
    config.getInterceptors().addLast(contextInspectingInterceptorCfg);
    config.getMultiTenancy().setEnabled(false);

    // reset calls from previous tests
    TestTenantProvidingInterceptor.resetInterceptorsCalls();

    // when
    gateway.start().join();
    try (final var client = createZeebeClient()) {
      try {
        // TODO: capture Broker request to assert that tenant id is set to <default>
        final DeploymentEvent response =
            client
                .newDeployResourceCommand()
                .addResourceFromClasspath("processes/one-task-process.bpmn")
                .send()
                .join();

        // then
        // the tenant authorizations list is still set on the gRPC context
        assertThat(ContextInspectingInterceptor.CONTEXT_TENANT_IDS.get()).isNotNull();
        assertThat(ContextInspectingInterceptor.CONTEXT_TENANT_IDS.get())
            .containsExactlyInAnyOrderElementsOf(List.of("tenant-1"));
        // but the tenant authorizations list is ignored, and the <default> tenant is used
        assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      } catch (final ClientStatusException ignored) {
        // ignore any errors, we just really care that the interceptor was called
      }
    }
  }

  private ZeebeClient createZeebeClient() {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(
            NetUtil.toSocketAddressString(gateway.getGatewayCfg().getNetwork().toSocketAddress()))
        .usePlaintext()
        .build();
  }
}
