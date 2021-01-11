/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerDeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowMetadata;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import org.junit.Test;

public final class DeployWorkflowTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final DeployWorkflowStub stub = new DeployWorkflowStub();
    stub.registerWith(brokerClient);

    final String bpmnName = "testProcess.bpmn";
    final String otherName = "testProcess.txt";

    final Builder builder = DeployWorkflowRequest.newBuilder();
    builder
        .addWorkflowsBuilder()
        .setName(bpmnName)
        .setDefinition(ByteString.copyFromUtf8("<xml/>"));
    builder.addWorkflowsBuilder().setName(otherName).setDefinition(ByteString.copyFromUtf8("test"));

    final DeployWorkflowRequest request = builder.build();

    // when
    final DeployWorkflowResponse response = client.deployWorkflow(request);

    // then
    assertThat(response.getKey()).isEqualTo(stub.getKey());
    assertThat(response.getWorkflowsCount()).isEqualTo(2);

    WorkflowMetadata workflow = response.getWorkflows(0);
    assertThat(workflow.getBpmnProcessId()).isEqualTo(bpmnName);
    assertThat(workflow.getResourceName()).isEqualTo(bpmnName);
    assertThat(workflow.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
    assertThat(workflow.getVersion()).isEqualTo(stub.getWorkflowVersion());

    workflow = response.getWorkflows(1);
    assertThat(workflow.getBpmnProcessId()).isEqualTo(otherName);
    assertThat(workflow.getResourceName()).isEqualTo(otherName);
    assertThat(workflow.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
    assertThat(workflow.getVersion()).isEqualTo(stub.getWorkflowVersion());

    final BrokerDeployWorkflowRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.DEPLOYMENT);
  }
}
