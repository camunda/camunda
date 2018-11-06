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

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerGetWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowResponse;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.data.repository.GetWorkflowControlRequest;
import org.junit.Test;

public class GetWorkflowTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final GetWorkflowStub stub = new GetWorkflowStub();
    stub.registerWith(gateway);

    final GetWorkflowRequest request =
        GetWorkflowRequest.newBuilder()
            .setWorkflowKey(123L)
            .setBpmnProcessId("foo")
            .setVersion(333)
            .build();

    // when
    final GetWorkflowResponse response = client.getWorkflow(request);

    // then
    assertThat(response.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
    assertThat(response.getBpmnProcessId()).isEqualTo(stub.getBpmnProcessId());
    assertThat(response.getVersion()).isEqualTo(stub.getVersion());
    assertThat(response.getResourceName()).isEqualTo(stub.getResourceName());
    assertThat(response.getBpmnXml()).isEqualTo(stub.getBpmnXml());

    final BrokerGetWorkflowRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getMessageType()).isEqualTo(ControlMessageType.GET_WORKFLOW);

    final GetWorkflowControlRequest brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getWorkflowKey()).isEqualTo(123L);
    assertThat(brokerRequestValue.getBpmnProcessId()).isEqualTo(wrapString("foo"));
    assertThat(brokerRequestValue.getVersion()).isEqualTo(333);
  }
}
