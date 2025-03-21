/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.AtomixCluster;
import io.atomix.utils.net.Address;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.security.configuration.SecurityConfigurations;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClientImpl;
import io.camunda.zeebe.gateway.interceptors.util.ContextInspectingInterceptor;
import io.camunda.zeebe.gateway.interceptors.util.TestInterceptor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.util.NetUtil;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

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
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

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
            () -> cluster.getMembershipService().getMembers(),
            new BrokerClientTopologyMetrics(meterRegistry));
    cluster.getMembershipService().addListener(topologyManager);

    brokerClient =
        new BrokerClientImpl(
            config.getCluster().getRequestTimeout(),
            cluster.getMessagingService(),
            cluster.getEventService(),
            scheduler,
            topologyManager,
            new BrokerClientRequestMetrics(meterRegistry));

    jobStreamClient =
        new JobStreamClientImpl(scheduler, cluster.getCommunicationService(), meterRegistry);
    gateway =
        new Gateway(
            config,
            SecurityConfigurations.unauthenticated(),
            brokerClient,
            scheduler,
            jobStreamClient.streamer(),
            mock(UserServices.class),
            mock(PasswordEncoder.class),
            new SimpleMeterRegistry(),
            mock(JwtDecoder.class));

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
    try (final var client = createCamundaClient()) {
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
    try (final var client = createCamundaClient()) {
      try {
        client.newTopologyRequest().send().join();
      } catch (final ClientStatusException ignored) {
        // ignore any errors, we just really care that the interceptor was called
      }
    }

    // then
    assertThat(ContextInspectingInterceptor.CONTEXT_QUERY_API.get()).isNotNull();
  }

  private CamundaClient createCamundaClient() {
    return CamundaClient.newClientBuilder()
        .gatewayAddress(
            NetUtil.toSocketAddressString(gateway.getGatewayCfg().getNetwork().toSocketAddress()))
        .usePlaintext()
        .build();
  }
}
