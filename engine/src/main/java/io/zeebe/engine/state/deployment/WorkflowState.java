/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.deployment;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.NextValueManager;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.instance.TimerInstanceState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import java.util.Collection;
import org.agrona.DirectBuffer;

public final class WorkflowState {

  private final NextValueManager versionManager;
  private final WorkflowPersistenceCache workflowPersistenceCache;
  private final TimerInstanceState timerInstanceState;
  private final ElementInstanceState elementInstanceState;
  private final EventScopeInstanceState eventScopeInstanceState;

  public WorkflowState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final DbContext dbContext,
      final KeyGenerator keyGenerator) {
    versionManager = new NextValueManager(zeebeDb, dbContext, ZbColumnFamilies.WORKFLOW_VERSION);
    workflowPersistenceCache = new WorkflowPersistenceCache(zeebeDb, dbContext);
    timerInstanceState = new TimerInstanceState(zeebeDb, dbContext);
    elementInstanceState = new ElementInstanceState(zeebeDb, dbContext, keyGenerator);
    eventScopeInstanceState = new EventScopeInstanceState(zeebeDb, dbContext);
  }

  public int getNextWorkflowVersion(final String bpmnProcessId) {
    return (int) versionManager.getNextValue(bpmnProcessId);
  }

  public void putDeployment(final DeploymentRecord deploymentRecord) {
    workflowPersistenceCache.putDeployment(deploymentRecord);
  }

  public DeployedWorkflow getWorkflowByProcessIdAndVersion(
      final DirectBuffer bpmnProcessId, final int version) {
    return workflowPersistenceCache.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);
  }

  public DeployedWorkflow getWorkflowByKey(final long workflowKey) {
    return workflowPersistenceCache.getWorkflowByKey(workflowKey);
  }

  public DeployedWorkflow getLatestWorkflowVersionByProcessId(final DirectBuffer bpmnProcessId) {
    return workflowPersistenceCache.getLatestWorkflowVersionByProcessId(bpmnProcessId);
  }

  public Collection<DeployedWorkflow> getWorkflows() {
    return workflowPersistenceCache.getWorkflows();
  }

  public Collection<DeployedWorkflow> getWorkflowsByBpmnProcessId(final DirectBuffer processId) {
    return workflowPersistenceCache.getWorkflowsByBpmnProcessId(processId);
  }

  public void putLatestVersionDigest(final DirectBuffer processId, final DirectBuffer digest) {
    workflowPersistenceCache.putLatestVersionDigest(processId, digest);
  }

  public DirectBuffer getLatestVersionDigest(final DirectBuffer processId) {
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

  public <T extends ExecutableFlowElement> T getFlowElement(
      final long workflowKey, final DirectBuffer elementId, final Class<T> elementType) {

    final var deployedWorkflow = getWorkflowByKey(workflowKey);
    if (deployedWorkflow == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find a workflow deployed with key '%d' but not found.", workflowKey));
    }

    final var workflow = deployedWorkflow.getWorkflow();
    final var element = workflow.getElementById(elementId, elementType);
    if (element == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find a flow element with id '%s' in workflow with key '%d' but not found.",
              bufferAsString(elementId), workflowKey));
    }

    return element;
  }
}
