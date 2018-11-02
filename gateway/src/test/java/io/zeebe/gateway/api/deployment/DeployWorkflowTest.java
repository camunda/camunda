/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.intent.DeploymentIntent;
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
            io.zeebe.protocol.impl.record.value.deployment.ResourceType.BPMN_XML,
            io.zeebe.protocol.impl.record.value.deployment.ResourceType.YAML_WORKFLOW);
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
            io.zeebe.protocol.impl.record.value.deployment.ResourceType.BPMN_XML,
            io.zeebe.protocol.impl.record.value.deployment.ResourceType.YAML_WORKFLOW);
  }
}
