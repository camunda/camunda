/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.gateway.cmd.BrokerRejectionException;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class GatewayIntegrationTest {

  @Rule
  public EmbeddedBrokerRule broker =
      new EmbeddedBrokerRule(brokerCfg -> brokerCfg.getGateway().setEnable(false));

  @Rule public final ExpectedException exception = ExpectedException.none();

  private BrokerClientImpl client;

  @Before
  public void setup() {
    final GatewayCfg configuration = new GatewayCfg();
    final var brokerCfg = broker.getBrokerCfg();
    final var internalApi = brokerCfg.getNetwork().getInternalApi();
    configuration
        .getCluster()
        .setHost("0.0.0.0")
        .setPort(SocketUtil.getNextAddress().getPort())
        .setContactPoint(internalApi.toString())
        .setRequestTimeout(Duration.ofSeconds(10));
    configuration.init();

    final ControlledActorClock clock = new ControlledActorClock();
    final ClusterServices clusterServices = broker.getClusterServices();
    client =
        new BrokerClientImpl(
            configuration,
            clusterServices.getMessagingService(),
            clusterServices.getMembershipService(),
            clusterServices.getEventService(),
            clock);
  }

  @After
  public void tearDown() {
    client.close();
  }

  @Test
  public void shouldReturnRejectionWithCorrectTypeAndReason() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Throwable> errorResponse = new AtomicReference<>();

    // when
    client.sendRequestWithRetry(
        new BrokerCreateProcessInstanceRequest(),
        (k, r) -> {},
        error -> {
          errorResponse.set(error);
          latch.countDown();
        });

    // then
    latch.await();
    final var error = errorResponse.get();
    assertThat(error).isInstanceOf(BrokerRejectionException.class);
    final BrokerRejection rejection = ((BrokerRejectionException) error).getRejection();
    assertThat(rejection.getType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getReason())
        .isEqualTo("Expected at least a bpmnProcessId or a key greater than -1, but none given");
  }
}
