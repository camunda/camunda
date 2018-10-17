/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.camunda.operate.zeebeimport.record.value.deployment;

import java.util.Objects;
import io.zeebe.exporter.record.value.deployment.DeployedWorkflow;

public class DeployedWorkflowImpl implements DeployedWorkflow {
  private String bpmnProcessId;
  private String resourceName;
  private long workflowKey;
  private int version;

  public DeployedWorkflowImpl() {
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public void setWorkflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeployedWorkflowImpl that = (DeployedWorkflowImpl) o;
    return workflowKey == that.workflowKey
        && version == that.version
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(resourceName, that.resourceName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bpmnProcessId, resourceName, workflowKey, version);
  }

  @Override
  public String toString() {
    return "DeployedWorkflowImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", workflowKey="
        + workflowKey
        + ", version="
        + version
        + '}';
  }
}
