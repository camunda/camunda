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
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject.ResourceType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import org.junit.Test;

public class DeployWorkflowTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final DeployWorkflowStub stub = new DeployWorkflowStub();
    stub.registerWith(gateway);

    final String bpmnName = "testProcess.bpmn";
    final String yamlName = "testProcess.yaml";

    final Builder builder = DeployWorkflowRequest.newBuilder();
    builder
        .addWorkflowsBuilder()
        .setName(bpmnName)
        .setType(ResourceType.BPMN)
        .setDefinition(ByteString.copyFromUtf8("<xml/>"));
    builder
        .addWorkflowsBuilder()
        .setName(yamlName)
        .setType(ResourceType.YAML)
        .setDefinition(ByteString.copyFromUtf8("yaml"));

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
    assertThat(workflow.getBpmnProcessId()).isEqualTo(yamlName);
    assertThat(workflow.getResourceName()).isEqualTo(yamlName);
    assertThat(workflow.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
    assertThat(workflow.getVersion()).isEqualTo(stub.getWorkflowVersion());

    final BrokerDeployWorkflowRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.DEPLOYMENT);
  }

  @Test
  public void shouldDetermineResourceTypeBasedOnFileExtension() {
    // given
    final DeployWorkflowStub stub = new DeployWorkflowStub();
    stub.registerWith(gateway);

    final Builder builder = DeployWorkflowRequest.newBuilder();
    builder
        .addWorkflowsBuilder()
        .setName("testProcess.bpmn")
        .setDefinition(ByteString.copyFromUtf8("<xml/>"));
    builder
        .addWorkflowsBuilder()
        .setName("testProcess.yaml")
        .setDefinition(ByteString.copyFromUtf8("yaml"));

    final DeployWorkflowRequest request = builder.build();

    // when
    client.deployWorkflow(request);

    // then
    final BrokerDeployWorkflowRequest brokerRequest = gateway.getSingleBrokerRequest();
    final DeploymentRecord record = brokerRequest.getRequestWriter();

    assertThat(record.resources())
        .extracting(DeploymentResource::getResourceType)
        .containsExactlyInAnyOrder(
            io.zeebe.protocol.record.value.deployment.ResourceType.BPMN_XML,
            io.zeebe.protocol.record.value.deployment.ResourceType.YAML_WORKFLOW);
  }

  @Test
  public void shouldAcceptProvidedResourceTypes() {
    // given
    final DeployWorkflowStub stub = new DeployWorkflowStub();
    stub.registerWith(gateway);

    final Builder builder = DeployWorkflowRequest.newBuilder();
    builder
        .addWorkflowsBuilder()
        .setName("testProcess.txt")
        .setType(ResourceType.BPMN)
        .setDefinition(ByteString.copyFromUtf8("<xml/>"));
    builder
        .addWorkflowsBuilder()
        .setName("testProcess.txt")
        .setType(ResourceType.YAML)
        .setDefinition(ByteString.copyFromUtf8("yaml"));

    final DeployWorkflowRequest request = builder.build();

    // when
    client.deployWorkflow(request);

    // then
    final BrokerDeployWorkflowRequest brokerRequest = gateway.getSingleBrokerRequest();
    final DeploymentRecord record = brokerRequest.getRequestWriter();

    assertThat(record.resources())
        .extracting(DeploymentResource::getResourceType)
        .containsExactlyInAnyOrder(
            io.zeebe.protocol.record.value.deployment.ResourceType.BPMN_XML,
            io.zeebe.protocol.record.value.deployment.ResourceType.YAML_WORKFLOW);
  }
}
