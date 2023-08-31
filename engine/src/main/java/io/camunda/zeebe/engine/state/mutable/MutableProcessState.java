/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;

public interface MutableProcessState extends ProcessState {

  void putDeployment(DeploymentRecord deploymentRecord);

  void putLatestVersionDigest(final ProcessRecord processRecord);

  void putProcess(long key, ProcessRecord value);

  /**
   * Updates the state of a process. This method updates both the ColumnFamily and the in memory
   * cache.
   *
   * @param processRecord the record of the process that is updated
   * @param state the new state
   */
  void updateProcessState(final ProcessRecord processRecord, final PersistedProcessState state);

  /**
   * Deletes a process fromm the state and cache
   *
   * @param processRecord the record of the process that is deleted
   */
  void deleteProcess(final ProcessRecord processRecord);
}
