/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.process;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.List;
import org.junit.Test;

public final class CreateProcessInstanceWithResultTest extends GatewayTest {

  @Test
  public void shouldMapToBrokerRequest() {
    // given
    final CreateProcessInstanceWithResultStub stub = new CreateProcessInstanceWithResultStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceWithResultRequest request =
        CreateProcessInstanceWithResultRequest.newBuilder()
            .setRequest(
                CreateProcessInstanceRequest.newBuilder()
                    .setProcessDefinitionKey(stub.getProcessDefinitionKey()))
            .addAllFetchVariables(List.of("x"))
            .build();

    // when
    client.createProcessInstanceWithResult(request);

    // then
    final BrokerCreateProcessInstanceWithResultRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent())
        .isEqualTo(ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_CREATION);

    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getProcessDefinitionKey())
        .isEqualTo(stub.getProcessDefinitionKey());
    assertThat(brokerRequestValue.fetchVariables().iterator().next().getValue())
        .isEqualTo(wrapString("x"));
  }

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CreateProcessInstanceWithResultStub stub = new CreateProcessInstanceWithResultStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceWithResultRequest request =
        CreateProcessInstanceWithResultRequest.newBuilder()
            .setRequest(
                CreateProcessInstanceRequest.newBuilder()
                    .setProcessDefinitionKey(stub.getProcessDefinitionKey()))
            .build();

    // when
    final CreateProcessInstanceWithResultResponse response =
        client.createProcessInstanceWithResult(request);

    // then
    assertThat(response.getBpmnProcessId()).isEqualTo(stub.getProcessId());
    assertThat(response.getVersion()).isEqualTo(stub.getProcessVersion());
    assertThat(response.getProcessDefinitionKey()).isEqualTo(stub.getProcessDefinitionKey());
    assertThat(response.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());
  }
}
