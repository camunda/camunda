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
package org.camunda.operate.zeebeimport.record.value;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.camunda.operate.zeebeimport.record.value.deployment.DeployedWorkflowImpl;
import org.camunda.operate.zeebeimport.record.value.deployment.DeploymentResourceImpl;
import io.zeebe.exporter.record.RecordValue;
import io.zeebe.exporter.record.value.DeploymentRecordValue;
import io.zeebe.exporter.record.value.deployment.DeployedWorkflow;
import io.zeebe.exporter.record.value.deployment.DeploymentResource;

public class DeploymentRecordValueImpl implements DeploymentRecordValue, RecordValue {
  private List<DeployedWorkflowImpl> deployedWorkflows;
  private List<DeploymentResourceImpl> resources;

  public DeploymentRecordValueImpl() {
  }

  @Override
  public List<DeployedWorkflow> getDeployedWorkflows() {
    return Arrays.asList(deployedWorkflows.toArray(new DeployedWorkflow[deployedWorkflows.size()]));
  }

  @Override
  public List<DeploymentResource> getResources() {
    return Arrays.asList(resources.toArray(new DeploymentResourceImpl[resources.size()]));
  }

  public void setDeployedWorkflows(List<DeployedWorkflowImpl> deployedWorkflows) {
    this.deployedWorkflows = deployedWorkflows;
  }

  public void setResources(List<DeploymentResourceImpl> resources) {
    this.resources = resources;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeploymentRecordValueImpl that = (DeploymentRecordValueImpl) o;
    return Objects.equals(deployedWorkflows, that.deployedWorkflows)
        && Objects.equals(resources, that.resources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deployedWorkflows, resources);
  }

  @Override
  public String toString() {
    return "DeploymentRecordValueImpl{"
        + "deployedWorkflows="
        + deployedWorkflows
        + ", resources="
        + resources
        + '}';
  }
}
