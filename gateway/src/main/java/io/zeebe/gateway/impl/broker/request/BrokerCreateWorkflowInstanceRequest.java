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

import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import org.agrona.DirectBuffer;

public class BrokerCreateWorkflowInstanceRequest
    extends BrokerExecuteCommand<WorkflowInstanceCreationRecord> {

  private final WorkflowInstanceCreationRecord requestDto = new WorkflowInstanceCreationRecord();

  public BrokerCreateWorkflowInstanceRequest() {
    super(ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE);
  }

  public BrokerCreateWorkflowInstanceRequest setBpmnProcessId(String bpmnProcessId) {
    requestDto.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  public BrokerCreateWorkflowInstanceRequest setKey(long key) {
    requestDto.setKey(key);
    return this;
  }

  public BrokerCreateWorkflowInstanceRequest setVersion(int version) {
    requestDto.setVersion(version);
    return this;
  }

  public BrokerCreateWorkflowInstanceRequest setVariables(DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  @Override
  public WorkflowInstanceCreationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected WorkflowInstanceCreationRecord toResponseDto(DirectBuffer buffer) {
    final WorkflowInstanceCreationRecord responseDto = new WorkflowInstanceCreationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
