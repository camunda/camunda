/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceResponse;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import org.junit.Test;

public final class CreateWorkflowInstanceTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CreateWorkflowInstanceStub stub = new CreateWorkflowInstanceStub();
    stub.registerWith(brokerClient);

    final CreateWorkflowInstanceRequest request =
        CreateWorkflowInstanceRequest.newBuilder().setWorkflowKey(stub.getWorkflowKey()).build();

    // when
    final CreateWorkflowInstanceResponse response = client.createWorkflowInstance(request);

    // then
    assertThat(response.getBpmnProcessId()).isEqualTo(stub.getProcessId());
    assertThat(response.getVersion()).isEqualTo(stub.getProcessVersion());
    assertThat(response.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
    assertThat(response.getWorkflowInstanceKey()).isEqualTo(stub.getWorkflowInstanceKey());

    final BrokerCreateWorkflowInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(WorkflowInstanceCreationIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE_CREATION);

    final WorkflowInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
  }
}
