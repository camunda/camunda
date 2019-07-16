/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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

  public void putLatestVersionDigest(DirectBuffer processId, DirectBuffer digest) {
    workflowPersistenceCache.putLatestVersionDigest(processId, digest);
  }

  public DirectBuffer getLatestVersionDigest(DirectBuffer processId) {
    return workflowPersistenceCache.getLatestVersionDigest(processId);
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
