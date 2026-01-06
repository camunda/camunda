/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import java.util.Collections;
import java.util.List;

public final class ExecutableUserTask extends ExecutableJobWorkerTask {

  private UserTaskProperties userTaskProperties;

  private List<TaskListener> taskListeners = Collections.emptyList();

  public ExecutableUserTask(final String id) {
    super(id);
  }

  public UserTaskProperties getUserTaskProperties() {
    return userTaskProperties;
  }

  public void setUserTaskProperties(final UserTaskProperties userTaskProperties) {
    this.userTaskProperties = userTaskProperties;
  }

  public List<TaskListener> getTaskListeners(final ZeebeTaskListenerEventType eventType) {
    return taskListeners.stream().filter(tl -> tl.getEventType() == eventType).toList();
  }

  public List<TaskListener> getTaskListeners() {
    return taskListeners;
  }

  public void setTaskListeners(final List<TaskListener> taskListeners) {
    this.taskListeners = taskListeners;
  }

  public boolean hasTaskListeners(final ZeebeTaskListenerEventType eventType) {
    return !getTaskListeners(eventType).isEmpty();
  }

  public boolean hasTaskListeners() {
    return !getTaskListeners().isEmpty();
  }
}
