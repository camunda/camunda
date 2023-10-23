/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskListenerEventType;
import java.util.ArrayList;
import java.util.List;

public class ExecutableUserTask {

  private final List<UserTaskListener> listeners = new ArrayList<>();

  public void addListener(
      final String listenerName, final ZeebeUserTaskListenerEventType eventType) {
    final UserTaskListener listener = new UserTaskListener();
    listener.setName(listenerName);
    listener.setEventType(eventType);
    listeners.add(listener);
  }

  public List<UserTaskListener> getListeners() {
    return listeners;
  }
}
