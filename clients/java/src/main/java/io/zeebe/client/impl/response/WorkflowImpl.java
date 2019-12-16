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
package io.zeebe.client.impl.response;

import io.zeebe.client.api.response.Workflow;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowMetadata;
import java.util.Objects;

public final class WorkflowImpl implements Workflow {

  private final long workflowKey;
  private final String bpmnProcessId;
  private final int version;
  private final String resourceName;

  public WorkflowImpl(final WorkflowMetadata workflow) {
    this(
        workflow.getWorkflowKey(),
        workflow.getBpmnProcessId(),
        workflow.getVersion(),
        workflow.getResourceName());
  }

  public WorkflowImpl(
      final long workflowKey,
      final String bpmnProcessId,
      final int version,
      final String resourceName) {
    this.workflowKey = workflowKey;
    this.bpmnProcessId = bpmnProcessId;
    this.version = version;
    this.resourceName = resourceName;
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
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public int hashCode() {
    return Objects.hash(workflowKey, bpmnProcessId, version, resourceName);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WorkflowImpl workflow = (WorkflowImpl) o;
    return workflowKey == workflow.workflowKey
        && version == workflow.version
        && Objects.equals(bpmnProcessId, workflow.bpmnProcessId)
        && Objects.equals(resourceName, workflow.resourceName);
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
