/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.listview.VariableListViewEntity;
import java.util.Collection;
import java.util.List;

public interface ListViewStore {
  /**
   * Remove the task variable data for the given task's flow node instance ID. This will be used
   * meanwhile we still support Job Workers, and to clean up the task variable data once the task is
   * completed from a Job Worker side.
   *
   * @param flowNodeInstanceId the flow node ID of the task
   */
  void removeVariableByFlowNodeInstanceId(final String flowNodeInstanceId);

  /**
   * Get Task Variable by Variable Name.
   *
   * @param varName the flow node ID of the task
   * @return the task variables
   */
  List<VariableListViewEntity> getVariablesByVariableName(final String varName);

  /**
   * Persist the task variables.
   *
   * @param variable the task variables
   */
  void persistTaskVariables(final Collection<VariableListViewEntity> variable);
}
