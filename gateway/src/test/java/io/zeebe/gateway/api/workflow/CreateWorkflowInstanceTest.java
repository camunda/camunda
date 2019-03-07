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
package io.zeebe.gateway.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceResponse;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import org.junit.Test;

public class CreateWorkflowInstanceTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CreateWorkflowInstanceStub stub = new CreateWorkflowInstanceStub();
    stub.registerWith(gateway);

    final CreateWorkflowInstanceRequest request =
        CreateWorkflowInstanceRequest.newBuilder().setWorkflowKey(stub.getWorkflowKey()).build();

    // when
    final CreateWorkflowInstanceResponse response = client.createWorkflowInstance(request);

    // then
    assertThat(response.getBpmnProcessId()).isEqualTo(stub.getProcessId());
    assertThat(response.getVersion()).isEqualTo(stub.getProcessVersion());
    assertThat(response.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
    assertThat(response.getWorkflowInstanceKey()).isEqualTo(stub.getWorkflowInstanceKey());

    final BrokerCreateWorkflowInstanceRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(WorkflowInstanceCreationIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE_CREATION);

    final WorkflowInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getKey()).isEqualTo(stub.getWorkflowKey());
  }
}
