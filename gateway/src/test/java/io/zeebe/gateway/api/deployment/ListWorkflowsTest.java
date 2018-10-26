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

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.deployment.ListWorkflowsStub.Workflow;
import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerListWorkflowsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowMetadata;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.data.repository.ListWorkflowsControlRequest;
import java.util.List;
import org.junit.Test;

public class ListWorkflowsTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final ListWorkflowsStub stub = new ListWorkflowsStub();
    stub.registerWith(gateway);

    final ListWorkflowsRequest request =
        ListWorkflowsRequest.newBuilder().setBpmnProcessId("test").build();

    // when
    final ListWorkflowsResponse response = client.listWorkflows(request);

    // then
    final List<Workflow> expectedWorkflows = stub.getWorkflows();
    assertThat(response.getWorkflowsCount()).isEqualTo(expectedWorkflows.size());
    for (int i = 0; i < response.getWorkflowsCount(); i++) {
      final WorkflowMetadata actual = response.getWorkflows(i);
      final Workflow expected = expectedWorkflows.get(i);
      assertThat(actual.getWorkflowKey()).isEqualTo(expected.getWorkflowKey());
      assertThat(actual.getBpmnProcessId()).isEqualTo(expected.getBpmnProcessId());
      assertThat(actual.getVersion()).isEqualTo(expected.getVersion());
      assertThat(actual.getResourceName()).isEqualTo(expected.getResourceName());
    }

    final BrokerListWorkflowsRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getMessageType()).isEqualTo(ControlMessageType.LIST_WORKFLOWS);

    final ListWorkflowsControlRequest brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getBpmnProcessId()).isEqualTo(wrapString("test"));
  }
}
