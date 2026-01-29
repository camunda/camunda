/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.queryapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteQueryRequest;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteQueryResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.ClientRequest;
import io.camunda.zeebe.transport.ClientTransport;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixClientTransportAdapter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.netty.util.NetUtil;
import java.time.Duration;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class QueryApiIT {

  public final ActorSchedulerRule actor = new ActorSchedulerRule();
  public final EmbeddedBrokerRule broker =
      new EmbeddedBrokerRule(cfg -> cfg.getExperimental().getQueryApi().setEnabled(true));
  public final CommandApiRule command = new CommandApiRule(broker::getAtomixCluster);

  @Rule
  public final RuleChain ruleChain = RuleChain.outerRule(broker).around(command).around(actor);

  private ClientTransport clientTransport;
  private String serverAddress;

  @Before
  public void setup() {
    serverAddress =
        NetUtil.toSocketAddressString(
            broker.getBrokerCfg().getNetwork().getCommandApi().getAddress());
    final var messagingService =
        new NettyMessagingService(
            broker.getBrokerCfg().getCluster().getClusterName(),
            Address.from(SocketUtil.getNextAddress().getPort()),
            new MessagingConfig(),
            broker.getMeterRegistry());

    clientTransport = new AtomixClientTransportAdapter(messagingService);
    actor.submitActor((AtomixClientTransportAdapter) clientTransport).join();
    messagingService.start().join();
  }

  @Test
  public void shouldRespondWithErrorWhenDisabled() {
    // given
    broker.getBrokerCfg().getExperimental().getQueryApi().setEnabled(false);

    // when
    final DirectBuffer response =
        clientTransport
            .sendRequest(
                () -> serverAddress,
                new Request().partitionId(1).key(123L).valueType(ValueType.PROCESS),
                Duration.ofSeconds(10))
            .join();

    // then
    assertThat(response).isNotNull();
    final var result = new ErrorResponse();
    final var length = response.capacity();
    result.wrap(response, 0, length);

    assertThat(result.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
    assertThat(BufferUtil.bufferAsString(result.getErrorData()))
        .isEqualTo(
            "Failed to handle query as the query API is disabled; did you configure"
                + " zeebe.broker.experimental.queryapi.enabled?");
  }

  @Test
  public void shouldRespondWithBpmnProcessIdWhenProcessFound() {
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
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.CREATED)
                .withRecordKey(key)
                .limit(1)
                .exists())
        .as("wait until the process definition actually exists in the state")
        .isTrue();

    // when
    final DirectBuffer response =
        clientTransport
            .sendRequest(
                () -> serverAddress,
                new Request().partitionId(1).key(key).valueType(ValueType.PROCESS),
                Duration.ofSeconds(10))
            .join();

    final var result = new ExecuteQueryResponse();
    result.wrap(response, 0, response.capacity());
    assertThat(result)
        .extracting(ExecuteQueryResponse::getBpmnProcessId)
        .isEqualTo("OneProcessToRuleThemAll");
  }

  @Test
  public void shouldRespondWithBpmnProcessIdWhenProcessInstanceFound() {
    // given
    final var client = command.partitionClient(1);
    client.deploy(
        Bpmn.createExecutableProcess("OneProcessToRuleThemAll")
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .endEvent()
            .done());
    final long key =
        client
            .createProcessInstance(r -> r.setBpmnProcessId("OneProcessToRuleThemAll"))
            .getProcessInstanceKey();
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withProcessInstanceKey(key)
                .filterRootScope()
                .limit(1)
                .exists())
        .as("wait until the element instance actually exists in the state")
        .isTrue();

    // when
    final DirectBuffer response =
        clientTransport
            .sendRequest(
                () -> serverAddress,
                new Request().partitionId(1).key(key).valueType(ValueType.PROCESS_INSTANCE),
                Duration.ofSeconds(10))
            .join();

    final var result = new ExecuteQueryResponse();
    result.wrap(response, 0, response.capacity());
    assertThat(result)
        .extracting(ExecuteQueryResponse::getBpmnProcessId)
        .isEqualTo("OneProcessToRuleThemAll");
  }

  @Test
  public void shouldRespondWithBpmnProcessIdWhenJobFound() {
    // given
    final var key = command.partitionClient(1).createJob("type");

    // when
    final DirectBuffer response =
        clientTransport
            .sendRequest(
                () -> serverAddress,
                new Request().partitionId(1).key(key).valueType(ValueType.JOB),
                Duration.ofSeconds(10))
            .join();

    final var result = new ExecuteQueryResponse();
    result.wrap(response, 0, response.capacity());
    assertThat(result).extracting(ExecuteQueryResponse::getBpmnProcessId).isEqualTo("process");
  }

  private static final class Request implements ClientRequest {
    private final ExecuteQueryRequest request = new ExecuteQueryRequest();

    public Request partitionId(final int partitionId) {
      request.setPartitionId(partitionId);
      return this;
    }

    public Request key(final long key) {
      request.setKey(key);
      return this;
    }

    public Request valueType(final ValueType valueType) {
      request.setValueType(valueType);
      return this;
    }

    @Override
    public int getPartitionId() {
      return request.getPartitionId();
    }

    @Override
    public RequestType getRequestType() {
      return RequestType.QUERY;
    }

    @Override
    public int getLength() {
      return request.getLength();
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return request.write(buffer, offset);
    }
  }
}
