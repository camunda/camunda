/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.state.deployment;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.state.NextValueManager;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.instance.TimerInstanceState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import java.util.Collection;
import org.agrona.DirectBuffer;

public class WorkflowState {

  private final NextValueManager versionManager;
  private final WorkflowPersistenceCache workflowPersistenceCache;
  private final TimerInstanceState timerInstanceState;
  private final ElementInstanceState elementInstanceState;
  private final EventScopeInstanceState eventScopeInstanceState;

  public WorkflowState(
      ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext, KeyGenerator keyGenerator) {
    versionManager = new NextValueManager(zeebeDb, dbContext, ZbColumnFamilies.WORKFLOW_VERSION);
    workflowPersistenceCache = new WorkflowPersistenceCache(zeebeDb, dbContext);
    timerInstanceState = new TimerInstanceState(zeebeDb, dbContext);
    elementInstanceState = new ElementInstanceState(zeebeDb, dbContext, keyGenerator);
    eventScopeInstanceState = new EventScopeInstanceState(zeebeDb, dbContext);
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

  public EventScopeInstanceState getEventScopeInstanceState() {
    return eventScopeInstanceState;
  }
}
