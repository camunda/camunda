/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.workflow;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceWithResultRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultResponse;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import java.util.List;
import org.junit.Test;

public final class CreateWorkflowInstanceWithResultTest extends GatewayTest {

  @Test
  public void shouldMapToBrokerRequest() {
    // given
    final CreateWorkflowInstanceWithResultStub stub = new CreateWorkflowInstanceWithResultStub();
    stub.registerWith(brokerClient);

    final CreateWorkflowInstanceWithResultRequest request =
        CreateWorkflowInstanceWithResultRequest.newBuilder()
            .setRequest(
                CreateWorkflowInstanceRequest.newBuilder().setWorkflowKey(stub.getWorkflowKey()))
            .addAllFetchVariables(List.of("x"))
            .build();

    // when
    client.createWorkflowInstanceWithResult(request);

    // then
    final BrokerCreateWorkflowInstanceWithResultRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent())
        .isEqualTo(WorkflowInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE_CREATION);

    final WorkflowInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
    assertThat(brokerRequestValue.fetchVariables().iterator().next().getValue())
        .isEqualTo(wrapString("x"));
  }

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CreateWorkflowInstanceWithResultStub stub = new CreateWorkflowInstanceWithResultStub();
    stub.registerWith(brokerClient);

    final CreateWorkflowInstanceWithResultRequest request =
        CreateWorkflowInstanceWithResultRequest.newBuilder()
            .setRequest(
                CreateWorkflowInstanceRequest.newBuilder().setWorkflowKey(stub.getWorkflowKey()))
            .build();

    // when
    final CreateWorkflowInstanceWithResultResponse response =
        client.createWorkflowInstanceWithResult(request);

    // then
    assertThat(response.getBpmnProcessId()).isEqualTo(stub.getProcessId());
    assertThat(response.getVersion()).isEqualTo(stub.getProcessVersion());
    assertThat(response.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
    assertThat(response.getWorkflowInstanceKey()).isEqualTo(stub.getWorkflowInstanceKey());
  }
}
