/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.externalapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.camunda.zeebe.test.broker.protocol.commandapi.ErrorResponseException;
import io.camunda.zeebe.test.broker.protocol.queryapi.ExecuteQueryRequest;
import io.camunda.zeebe.test.broker.protocol.queryapi.ExecuteQueryResponse;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.ClientTransport;
import io.camunda.zeebe.transport.impl.AtomixClientTransportAdapter;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import io.netty.util.NetUtil;
import java.time.Duration;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class QueryApiIT {

  public final ActorSchedulerRule actor = new ActorSchedulerRule();
  public final EmbeddedBrokerRule broker =
      new EmbeddedBrokerRule(
          cfg -> cfg.getNetwork().getExternalApi().getQueryApi().setEnabled(true));
  public final CommandApiRule command = new CommandApiRule(broker::getAtomixCluster);

  @Rule
  public final RuleChain ruleChain = RuleChain.outerRule(broker).around(command).around(actor);

  private ClientTransport clientTransport;
  private String serverAddress;

  @Before
  public void setup() {
    serverAddress =
        NetUtil.toSocketAddressString(
            broker.getBrokerCfg().getNetwork().getExternalApi().getAddress());
    final var messagingService =
        new NettyMessagingService(
            broker.getBrokerCfg().getCluster().getClusterName(),
            Address.from(SocketUtil.getNextAddress().getPort()),
            new MessagingConfig());

    clientTransport = new AtomixClientTransportAdapter(messagingService);
    actor.submitActor((AtomixClientTransportAdapter) clientTransport).join();
    messagingService.start().join();
  }

  @Test
  public void shouldRespondWithErrorWhenDisabled() {
    // given
    broker.getBrokerCfg().getNetwork().getExternalApi().getQueryApi().setEnabled(false);

    // when
    final DirectBuffer response =
        clientTransport
            .sendRequest(
                () -> serverAddress,
                new ExecuteQueryRequest().partitionId(1).key(123L).valueType(ValueType.PROCESS),
                Duration.ofSeconds(10))
            .join();

    // then
    assertThat(response).isNotNull();
    final var result = new ExecuteQueryResponse();
    final var length = response.capacity();
    assertThatThrownBy(() -> result.wrap(response, 0, length))
        .isInstanceOf(ErrorResponseException.class)
        .extracting(ErrorResponseException.class::cast)
        .extracting(ErrorResponseException::getErrorCode, ErrorResponseException::getErrorMessage)
        .containsExactly(
            ErrorCode.UNSUPPORTED_MESSAGE,
            "Expected to handle ExecuteQueryRequest, but QueryApi is disabled");
  }

  @Test
  public void shouldRespondWithBpmnProcessIdWhenFound() {
    // given
    final long key =
        command
            .partitionClient(1)
            .deployProcess(
                Bpmn.createExecutableProcess("OneProcessToRuleThemAll")
                    .startEvent()
                    .endEvent()
                    .done())
            .getProcessDefinitionKey();

    // when
    final DirectBuffer response =
        clientTransport
            .sendRequest(
                () -> serverAddress,
                // todo: consider whether partitionId is really necessary
                new ExecuteQueryRequest().partitionId(1).key(key).valueType(ValueType.PROCESS),
                Duration.ofSeconds(10))
            .join();

    final var result = new ExecuteQueryResponse();
    result.wrap(response, 0, response.capacity());
    assertThat(result)
        .extracting(ExecuteQueryResponse::getBpmnProcessId)
        .isEqualTo("OneProcessToRuleThemAll");
  }
}
