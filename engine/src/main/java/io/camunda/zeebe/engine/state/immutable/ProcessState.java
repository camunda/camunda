/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import java.util.Collection;
import org.agrona.DirectBuffer;

public interface ProcessState {

  DeployedProcess getLatestProcessVersionByProcessId(DirectBuffer processId);

  DeployedProcess getProcessByProcessIdAndVersion(DirectBuffer processId, int version);

  DeployedProcess getProcessByKey(long key);

  Collection<DeployedProcess> getProcesses();

  Collection<DeployedProcess> getProcessesByBpmnProcessId(DirectBuffer bpmnProcessId);

  DirectBuffer getLatestVersionDigest(DirectBuffer processId);

  /**
   * Gets the latest process version. This is the latest version for which we have a process in the
   * state. It is not necessarily the latest version we've ever known for this process id, as
   * process could be deleted.
   *
   * @param bpmnProcessId the id of the process
   */
  int getLatestProcessVersion(String bpmnProcessId);

  /**
   * Gets the next version a process of a given id will get. This is used, for example, when a new
   * deployment is done. Using this method we decide the version the newly deployed process gets.
   *
   * @param bpmnProcessId the id of the process
   */
  int getNextProcessVersion(String bpmnProcessId);

  <T extends ExecutableFlowElement> T getFlowElement(
      long processDefinitionKey, DirectBuffer elementId, Class<T> elementType);

  /** TODO: Remove the cache entirely from the immutable state */
  void clearCache();
}
