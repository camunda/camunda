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
package io.zeebe.broker.workflow.state;

import io.zeebe.broker.logstreams.state.ZbColumnFamilies;
import io.zeebe.db.ZeebeDb;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import java.util.Collection;
import org.agrona.DirectBuffer;

public class WorkflowState {

  private final NextValueManager versionManager;
  private final WorkflowPersistenceCache workflowPersistenceCache;
  private final TimerInstanceState timerInstanceState;
  private final ElementInstanceState elementInstanceState;

  public WorkflowState(ZeebeDb<ZbColumnFamilies> zeebeDb) {
    versionManager = new NextValueManager(zeebeDb, ZbColumnFamilies.WORKFLOW_VERSION);
    workflowPersistenceCache = new WorkflowPersistenceCache(zeebeDb);
    timerInstanceState = new TimerInstanceState(zeebeDb);
    elementInstanceState = new ElementInstanceState(zeebeDb);
  }

  public int getNextWorkflowVersion(String bpmnProcessId) {
    return (int) versionManager.getNextValue(bpmnProcessId);
  }

  public boolean putDeployment(long deploymentKey, DeploymentRecord deploymentRecord) {
    return workflowPersistenceCache.putDeployment(deploymentKey, deploymentRecord);
  }

  public DeployedWorkflow getWorkflowByProcessIdAndVersion(
      DirectBuffer bpmnProcessId, int version) {
    return workflowPersistenceCache.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);
  }

  public DeployedWorkflow getWorkflowByKey(long workflowKey) {
    return workflowPersistenceCache.getWorkflowByKey(workflowKey);
  }

  public DeployedWorkflow getLatestWorkflowVersionByProcessId(DirectBuffer bpmnProcessId) {
    return workflowPersistenceCache.getLatestWorkflowVersionByProcessId(bpmnProcessId);
  }

  public Collection<DeployedWorkflow> getWorkflows() {
    return workflowPersistenceCache.getWorkflows();
  }

  public Collection<DeployedWorkflow> getWorkflowsByBpmnProcessId(DirectBuffer processId) {
    return workflowPersistenceCache.getWorkflowsByBpmnProcessId(processId);
  }

  public TimerInstanceState getTimerState() {
    return timerInstanceState;
  }

  public ElementInstanceState getElementInstanceState() {
    return elementInstanceState;
  }
}
