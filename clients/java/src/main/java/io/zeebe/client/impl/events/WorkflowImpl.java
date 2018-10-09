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
package io.zeebe.client.impl.events;

import io.zeebe.client.api.commands.Workflow;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowMetadata;

public class WorkflowImpl implements Workflow {

  protected final long workflowKey;
  protected final String bpmnProcessId;
  protected final int version;
  protected final String resourceName;

  public WorkflowImpl(WorkflowMetadata workflow) {
    this(
        workflow.getWorkflowKey(),
        workflow.getBpmnProcessId(),
        workflow.getVersion(),
        workflow.getResourceName());
  }

  public WorkflowImpl(long workflowKey, String bpmnProcessId, int version, String resourceName) {
    this.workflowKey = workflowKey;
    this.bpmnProcessId = bpmnProcessId;
    this.version = version;
    this.resourceName = resourceName;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public String toString() {
    return "WorkflowImpl{"
        + "workflowKey="
        + workflowKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", resourceName='"
        + resourceName
        + '\''
        + '}';
  }
}
