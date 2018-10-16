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
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.data.repository.ListWorkflowsControlRequest;
import io.zeebe.protocol.impl.data.repository.ListWorkflowsResponse;
import org.agrona.DirectBuffer;

public class BrokerListWorkflowsRequest extends BrokerControlMessage<ListWorkflowsResponse> {

  private final ListWorkflowsControlRequest requestDto = new ListWorkflowsControlRequest();

  public BrokerListWorkflowsRequest() {
    super(ControlMessageType.LIST_WORKFLOWS);
  }

  public BrokerListWorkflowsRequest setBpmnProcessId(String bpmnProcessId) {
    requestDto.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  @Override
  public ListWorkflowsControlRequest getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ListWorkflowsResponse toResponseDto(DirectBuffer buffer) {
    final ListWorkflowsResponse responseDto = new ListWorkflowsResponse();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
