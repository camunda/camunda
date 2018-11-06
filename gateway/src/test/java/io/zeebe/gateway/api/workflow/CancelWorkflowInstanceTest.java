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
import io.zeebe.gateway.impl.broker.request.BrokerCancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceResponse;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.junit.Test;

public class CancelWorkflowInstanceTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CancelWorkflowInstanceStub stub = new CancelWorkflowInstanceStub();
    stub.registerWith(gateway);

    final CancelWorkflowInstanceRequest request =
        CancelWorkflowInstanceRequest.newBuilder().setWorkflowInstanceKey(123).build();

    // when
    final CancelWorkflowInstanceResponse response = client.cancelWorkflowInstance(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCancelWorkflowInstanceRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(123);
    assertThat(brokerRequest.getIntent()).isEqualTo(WorkflowInstanceIntent.CANCEL);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE);
  }
}
