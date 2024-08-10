/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import org.agrona.DirectBuffer;

public final class DeployedProcess {
  private final ExecutableProcess process;
  private final PersistedProcess persistedProcess;

  public DeployedProcess(final ExecutableProcess process, final PersistedProcess persistedProcess) {
    this.process = process;
    this.persistedProcess = persistedProcess;
  }

  public DirectBuffer getResourceName() {
    return persistedProcess.getResourceName();
  }

  public ExecutableProcess getProcess() {
    return process;
  }

  public int getVersion() {
    return persistedProcess.getVersion();
  }

  public long getKey() {
    return persistedProcess.getKey();
  }

  public DirectBuffer getResource() {
    return persistedProcess.getResource();
  }

  public DirectBuffer getBpmnProcessId() {
    return persistedProcess.getBpmnProcessId();
  }

  public PersistedProcessState getState() {
    return persistedProcess.getState();
  }

  public String getTenantId() {
    return persistedProcess.getTenantId();
  }

  public long getDeploymentKey() {
    return persistedProcess.getDeploymentKey();
  }

  @Override
  public String toString() {
    return "DeployedProcess{"
        + "process="
        + process
        + ", persistedProcess="
        + persistedProcess
        + '}';
  }
}
