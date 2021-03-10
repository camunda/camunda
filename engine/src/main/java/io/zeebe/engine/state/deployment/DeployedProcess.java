/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.deployment;

import io.zeebe.engine.processing.deployment.model.element.ExecutableWorkflow;
import org.agrona.DirectBuffer;

public final class DeployedWorkflow {
  private final ExecutableWorkflow workflow;
  private final PersistedWorkflow persistedWorkflow;

  public DeployedWorkflow(
      final ExecutableWorkflow workflow, final PersistedWorkflow persistedWorkflow) {
    this.workflow = workflow;
    this.persistedWorkflow = persistedWorkflow;
  }

  public DirectBuffer getResourceName() {
    return persistedWorkflow.getResourceName();
  }

  public ExecutableWorkflow getWorkflow() {
    return workflow;
  }

  public int getVersion() {
    return persistedWorkflow.getVersion();
  }

  public long getKey() {
    return persistedWorkflow.getKey();
  }

  public DirectBuffer getResource() {
    return persistedWorkflow.getResource();
  }

  public DirectBuffer getBpmnProcessId() {
    return persistedWorkflow.getBpmnProcessId();
  }

  @Override
  public String toString() {
    return "DeployedWorkflow{"
        + "workflow="
        + workflow
        + ", persistedWorkflow="
        + persistedWorkflow
        + '}';
  }
}
