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
package io.zeebe.broker.exporter.record.value.deployment;

import io.zeebe.exporter.api.record.value.deployment.DeployedWorkflow;
import java.util.Objects;

public class DeployedWorkflowImpl implements DeployedWorkflow {
  private final String bpmnProcessId;
  private final String resourceName;
  private final long workflowKey;
  private final int version;

  public DeployedWorkflowImpl(
      String bpmnProcessId, String resourceName, long workflowKey, int version) {
    this.bpmnProcessId = bpmnProcessId;
    this.resourceName = resourceName;
    this.workflowKey = workflowKey;
    this.version = version;
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
