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

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;

public class CreateWorkflowInstanceStub
    implements RequestStub<
        BrokerCreateWorkflowInstanceRequest, BrokerResponse<WorkflowInstanceRecord>> {

  public static final long WORKFLOW_INSTANCE_KEY = 123;
  public static final String PROCESS_ID = "process";
  public static final int PROCESS_VERSION = 1;
  public static final long WORKFLOW_KEY = 456;

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerCreateWorkflowInstanceRequest.class, this);
  }

  public long getWorkflowInstanceKey() {
    return WORKFLOW_INSTANCE_KEY;
  }

  public String getProcessId() {
    return PROCESS_ID;
  }

  public int getProcessVersion() {
    return PROCESS_VERSION;
  }

  public long getWorkflowKey() {
    return WORKFLOW_KEY;
  }

  @Override
  public BrokerResponse<WorkflowInstanceRecord> handle(BrokerCreateWorkflowInstanceRequest request)
      throws Exception {
    final WorkflowInstanceRecord response = new WorkflowInstanceRecord();
    response.setWorkflowInstanceKey(WORKFLOW_INSTANCE_KEY);
    response.setElementId(PROCESS_ID);
    response.setBpmnProcessId(PROCESS_ID);
    response.setPayload(request.getRequestWriter().getPayload());
    response.setScopeInstanceKey(-1);
    response.setVersion(PROCESS_VERSION);
    response.setWorkflowKey(WORKFLOW_KEY);

    return new BrokerResponse<>(response, 0, WORKFLOW_INSTANCE_KEY);
  }
}
