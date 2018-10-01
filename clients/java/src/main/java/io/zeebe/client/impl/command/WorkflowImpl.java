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
package io.zeebe.client.impl.command;

import io.zeebe.client.api.commands.Workflow;
import java.util.Objects;

public class WorkflowImpl implements Workflow {
  private String bpmnProcessId;
  private int version;
  private long workflowKey;
  private String resourceName;

  public WorkflowImpl(
      final String bpmnProcessId,
      final int version,
      final long workflowKey,
      final String resourceName) {
    this.bpmnProcessId = bpmnProcessId;
    this.version = version;
    this.workflowKey = workflowKey;
    this.resourceName = resourceName;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public void setVersion(final int version) {
    this.version = version;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  public void setWorkflowKey(final long workflowKey) {
    this.workflowKey = workflowKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(final String resourceName) {
    this.resourceName = resourceName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WorkflowImpl workflow = (WorkflowImpl) o;
    return version == workflow.version
        && workflowKey == workflow.workflowKey
        && Objects.equals(bpmnProcessId, workflow.bpmnProcessId)
        && Objects.equals(resourceName, workflow.resourceName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bpmnProcessId, version, workflowKey, resourceName);
  }

  @Override
  public String toString() {
    return "WorkflowImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", workflowKey="
        + workflowKey
        + ", resourceName='"
        + resourceName
        + '\''
        + '}';
  }
}
