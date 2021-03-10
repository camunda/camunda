/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import java.util.Collection;
import org.agrona.DirectBuffer;

public interface WorkflowState {

  DeployedWorkflow getLatestWorkflowVersionByProcessId(DirectBuffer processId);

  DeployedWorkflow getWorkflowByProcessIdAndVersion(DirectBuffer processId, int version);

  DeployedWorkflow getWorkflowByKey(long key);

  Collection<DeployedWorkflow> getWorkflows();

  Collection<DeployedWorkflow> getWorkflowsByBpmnProcessId(DirectBuffer bpmnProcessId);

  DirectBuffer getLatestVersionDigest(DirectBuffer processId);

  int getWorkflowVersion(String bpmnProcessId);

  <T extends ExecutableFlowElement> T getFlowElement(
      long workflowKey, DirectBuffer elementId, Class<T> elementType);
}
