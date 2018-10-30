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
import io.zeebe.protocol.intent.DeploymentIntent;
import org.junit.Test;

public class DeployWorkflowTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final DeployWorkflowStub stub = new DeployWorkflowStub();
    stub.registerWith(gateway);

    final Builder builder = DeployWorkflowRequest.newBuilder();
    builder
        .addWorkflowsBuilder()
        .setName("testProcess.bpmn")
        .setType(ResourceType.BPMN)
        .setDefinition(ByteString.copyFromUtf8("<xml/>"));
    builder
        .addWorkflowsBuilder()
        .setName("testProcess.yaml")
        .setType(ResourceType.YAML)
        .setDefinition(ByteString.copyFromUtf8("yaml"));

    final DeployWorkflowRequest request = builder.build();

    // when
    final DeployWorkflowResponse response = client.deployWorkflow(request);

    // then
    assertThat(response.getKey()).isEqualTo(stub.getKey());
    assertThat(response.getWorkflowsCount()).isEqualTo(2);

    WorkflowMetadata workflow = response.getWorkflows(0);
    assertThat(workflow.getBpmnProcessId()).isEqualTo("testProcess.bpmn");
    assertThat(workflow.getResourceName()).isEqualTo("testProcess.bpmn");
    assertThat(workflow.getWorkflowKey()).isEqualTo(ResourceType.BPMN.ordinal());
    assertThat(workflow.getVersion()).isEqualTo(ResourceType.BPMN.ordinal());

    workflow = response.getWorkflows(1);
    assertThat(workflow.getBpmnProcessId()).isEqualTo("testProcess.yaml");
    assertThat(workflow.getResourceName()).isEqualTo("testProcess.yaml");
    assertThat(workflow.getWorkflowKey()).isEqualTo(ResourceType.YAML.ordinal());
    assertThat(workflow.getVersion()).isEqualTo(ResourceType.YAML.ordinal());

    final BrokerDeployWorkflowRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.DEPLOYMENT);
  }
}
