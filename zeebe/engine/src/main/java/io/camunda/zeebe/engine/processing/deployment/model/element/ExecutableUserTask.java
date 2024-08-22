/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import java.util.List;
import java.util.Map;

public final class ExecutableUserTask extends ExecutableJobWorkerTask {

  private UserTaskProperties userTaskProperties;

  private Map<TaskListenerEventType, List<String>> taskListeners =
      Map.of(TaskListenerEventType.COMPLETE, List.of("listener_1", "listener_2"));

  public ExecutableUserTask(final String id) {
    super(id);
  }

  public UserTaskProperties getUserTaskProperties() {
    return userTaskProperties;
  }

  public void setUserTaskProperties(final UserTaskProperties userTaskProperties) {
    this.userTaskProperties = userTaskProperties;
  }

  public List<String> getTaskListeners(TaskListenerEventType type) {
    return taskListeners.getOrDefault(type, List.of());
  }

  public boolean hasTaskListeners(TaskListenerEventType type) {
    return taskListeners.containsKey(type);
  }

  public void setTaskListeners(final Map<TaskListenerEventType, List<String>> taskListeners) {
    this.taskListeners = taskListeners;
  }

  public enum TaskListenerEventType {
    CREATE,
    ASSIGN,
    UPDATE,
    COMPLETE
  }
}
