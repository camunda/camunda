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
package io.zeebe.gateway.impl.workflow;

import io.zeebe.gateway.api.commands.WorkflowResource;
import io.zeebe.gateway.api.commands.WorkflowResourceRequestStep1;
import io.zeebe.gateway.api.commands.WorkflowResourceRequestStep1.WorkflowResourceRequestStep2;
import io.zeebe.gateway.api.commands.WorkflowResourceRequestStep1.WorkflowResourceRequestStep3;
import io.zeebe.gateway.impl.ControlMessageRequest;
import io.zeebe.gateway.impl.RequestManager;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class WorkflowResourceRequestImpl extends ControlMessageRequest<WorkflowResource>
    implements WorkflowResourceRequestStep1,
        WorkflowResourceRequestStep2,
        WorkflowResourceRequestStep3 {
  private static final int LATEST_VERSION = -1;

  private final Request request;

  public WorkflowResourceRequestImpl(RequestManager client) {
    super(client, ControlMessageType.GET_WORKFLOW, WorkflowResourceImpl.class);

    setTargetPartition(Protocol.DEPLOYMENT_PARTITION);

    request = new Request();
  }

  @Override
  public WorkflowResourceRequestStep3 version(int version) {
    request.setVersion(version);
    return this;
  }

  @Override
  public WorkflowResourceRequestStep3 latestVersion() {
    request.setVersion(LATEST_VERSION);
    return this;
  }

  @Override
  public WorkflowResourceRequestStep2 bpmnProcessId(String bpmnProcessId) {
    request.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  @Override
  public WorkflowResourceRequestStep3 workflowKey(long workflowKey) {
    request.setWorkflowKey(workflowKey);
    return this;
  }

  @Override
  public Object getRequest() {
    return request;
  }

  class Request {
    private String bpmnProcessId;
    private int version = -1;
    private long workflowKey = -1L;

    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    public void setBpmnProcessId(String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
    }

    public int getVersion() {
      return version;
    }

    public void setVersion(int version) {
      this.version = version;
    }

    public long getWorkflowKey() {
      return workflowKey;
    }

    public void setWorkflowKey(long workflowKey) {
      this.workflowKey = workflowKey;
    }
  }
}
