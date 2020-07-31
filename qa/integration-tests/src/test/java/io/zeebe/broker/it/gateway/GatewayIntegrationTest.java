/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.test.util.socket.SocketUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
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
    final AtomixCluster atomixCluster = broker.getAtomix();
    client = new BrokerClientImpl(configuration, atomixCluster, clock);
  }

  @After
  public void tearDown() {
    client.close();
  }

  @Test
  public void shouldReturnRejectionWithCorrectTypeAndReason() {
    // given

    // when
    final BrokerResponse<WorkflowInstanceCreationRecord> response =
        client.sendRequest(new BrokerCreateWorkflowInstanceRequest()).join();

    // then
    assertThat(response.isRejection()).isTrue();
    final BrokerRejection error = response.getRejection();
    assertThat(error.getType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(error.getReason())
        .isEqualTo("Expected at least a bpmnProcessId or a key greater than -1, but none given");
  }
}
