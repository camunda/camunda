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
import io.zeebe.protocol.impl.data.repository.GetWorkflowControlRequest;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadataAndResource;
import org.agrona.DirectBuffer;

public class BrokerGetWorkflowRequest extends BrokerControlMessage<WorkflowMetadataAndResource> {

  private final GetWorkflowControlRequest requestDto = new GetWorkflowControlRequest();

  public BrokerGetWorkflowRequest() {
    super(ControlMessageType.GET_WORKFLOW);
  }

  public BrokerGetWorkflowRequest setWorkflowKey(long workflowKey) {
    requestDto.setWorkflowKey(workflowKey);
    return this;
  }

  public BrokerGetWorkflowRequest setBpmnProcessId(String bpmnProcessId) {
    requestDto.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  public BrokerGetWorkflowRequest setVersion(int version) {
    requestDto.setVersion(version);
    return this;
  }

  @Override
  public GetWorkflowControlRequest getRequestWriter() {
    return requestDto;
  }

  @Override
  protected WorkflowMetadataAndResource toResponseDto(DirectBuffer buffer) {
    final WorkflowMetadataAndResource responseDto = new WorkflowMetadataAndResource();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
