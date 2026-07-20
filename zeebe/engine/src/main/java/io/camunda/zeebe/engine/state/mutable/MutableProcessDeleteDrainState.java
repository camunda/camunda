/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ProcessDeleteDrainState;

public interface MutableProcessDeleteDrainState extends ProcessDeleteDrainState {

  /**
   * Records that the given partition still owes a drain report for the given process definition.
   */
  void addDrainingPartition(long processDefinitionKey, int partitionId);

  /** Clears the given partition's outstanding drain report for the given process definition. */
  void removeDrainingPartition(long processDefinitionKey, int partitionId);
}
