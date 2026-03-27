/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.GlobalListenersConfiguration;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.ArrayList;
import java.util.List;

public class GlobalListenersCfg implements ConfigurationEntry {

  /**
   * Configures global listeners that will be applied to all user tasks within the process and will
   * be triggered during task processing.
   */
  private List<GlobalListenerCfg> userTask = new ArrayList<>();

  /**
   * Configures global execution listeners that will be applied to process and element lifecycle
   * events across all processes without modifying BPMN models.
   */
  private List<GlobalListenerCfg> execution = new ArrayList<>();

  /**
   * Tracks how many entries in the {@link #execution} list originated from {@code
   * camunda.cluster.global-listeners.execution}. A value of {@code -1} means no merge with {@code
   * camunda.listener.execution} occurred (all entries are from global-listeners). Used by {@link
   * io.camunda.zeebe.broker.system.SystemContext} to log the correct source property path during
   * validation.
   */
  private int clusterExecutionCount = -1;

  public List<GlobalListenerCfg> getUserTask() {
    return userTask;
  }

  public void setUserTask(final List<GlobalListenerCfg> userTask) {
    this.userTask = userTask;
  }

  public List<GlobalListenerCfg> getExecution() {
    return execution;
  }

  public void setExecution(final List<GlobalListenerCfg> execution) {
    this.execution = execution;
  }

  public int getClusterExecutionCount() {
    return clusterExecutionCount;
  }

  public void setClusterExecutionCount(final int clusterExecutionCount) {
    this.clusterExecutionCount = clusterExecutionCount;
  }

  public GlobalListenersConfiguration createGlobalListenersConfiguration() {
    return new GlobalListenersConfiguration(
        userTask.stream()
            .map(cfg -> cfg.createGlobalListenerConfiguration(GlobalListenerType.USER_TASK))
            .toList(),
        execution.stream()
            .map(cfg -> cfg.createGlobalListenerConfiguration(GlobalListenerType.EXECUTION))
            .toList());
  }
}
