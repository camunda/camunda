/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import java.util.Optional;
import org.agrona.DirectBuffer;

public interface ProcessState {

  DeployedProcess getLatestProcessVersionByProcessId(DirectBuffer processId, final String tenantId);

  DeployedProcess getProcessByProcessIdAndVersion(
      DirectBuffer processId, int version, final String tenantId);

  DeployedProcess getProcessByProcessIdAndDeploymentKey(
      DirectBuffer processId, long deploymentKey, final String tenantId);

  DeployedProcess getProcessByKeyAndTenant(long key, String tenantId);

  DirectBuffer getLatestVersionDigest(DirectBuffer processId, final String tenantId);

  /**
   * Gets the latest process version. This is the latest version for which we have a process in the
   * state. It is not necessarily the latest version we've ever known for this process id, as
   * process could be deleted.
   *
   * @param bpmnProcessId the id of the process
   */
  int getLatestProcessVersion(String bpmnProcessId, final String tenantId);

  /**
   * Gets the next version a process of a given id will receive. This is used, for example, when a
   * new deployment is done. Using this method we decide the version the newly deployed process
   * receives.
   *
   * @param bpmnProcessId the id of the process
   */
  int getNextProcessVersion(String bpmnProcessId, final String tenantId);

  /**
   * Finds the previous known version a process. This is used, for example, when a process is
   * deleted and the timers of the previous process need to be activated.
   *
   * <p>If not previous version is found, an empty optional is returned.
   *
   * @param bpmnProcessId the id of the process
   * @param version the version for which we want to find the previous version
   */
  Optional<Integer> findProcessVersionBefore(
      String bpmnProcessId, long version, final String tenantId);

  <T extends ExecutableFlowElement> T getFlowElement(
      long processDefinitionKey, String tenantId, DirectBuffer elementId, Class<T> elementType);

  /** TODO: Remove the cache entirely from the immutable state */
  void clearCache();
}
