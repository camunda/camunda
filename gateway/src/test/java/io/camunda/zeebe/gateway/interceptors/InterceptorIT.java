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
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
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
import io.netty.util.NetUtil;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        AtomixCluster.builder()
            .withAddress(Address.from(clusterAddress.getHostName(), clusterAddress.getPort()))
            .build();
    brokerClient =
        new BrokerClientImpl(
            config.getCluster().getRequestTimeout(),
            cluster.getMessagingService(),
            cluster.getMembershipService(),
            cluster.getEventService(),
            scheduler);
    jobStreamClient = new JobStreamClientImpl(scheduler, cluster.getCommunicationService());
    gateway = new Gateway(config, brokerClient, scheduler, jobStreamClient.streamer());

    cluster.start().join();
    scheduler.start();
    jobStreamClient.start().join();
    brokerClient.start().forEach(ActorFuture::join);
    brokerClient.getTopologyManager().addTopologyListener(jobStreamClient);

    // gateway is purposefully not started to allow configuration changes
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(
        gateway, brokerClient, jobStreamClient, scheduler, () -> cluster.stop().join());
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

  private ZeebeClient createZeebeClient() {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(
            NetUtil.toSocketAddressString(gateway.getGatewayCfg().getNetwork().toSocketAddress()))
        .usePlaintext()
        .build();
  }
}
