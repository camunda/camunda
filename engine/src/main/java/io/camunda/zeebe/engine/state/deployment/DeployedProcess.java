/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.deployment;

import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
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
