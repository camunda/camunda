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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutableUserTask {

  private final Map<ZeebeUserTaskListenerEventType, List<UserTaskListener>> listeners =
      new HashMap<>();

  public void addListener(
      final String listenerName, final ZeebeUserTaskListenerEventType eventType) {
    final UserTaskListener listener = new UserTaskListener();
    listener.setName(listenerName);
    listener.setEventType(eventType);
    List<UserTaskListener> listenersPerEvent = listeners.get(listenerName);
    if (listenersPerEvent == null) {
      listenersPerEvent = new ArrayList<>();
    }
    listenersPerEvent.add(listener);
    listeners.put(eventType, listenersPerEvent);
  }

  public List<UserTaskListener> getListeners(final ZeebeUserTaskListenerEventType eventType) {
    return listeners.get(eventType);
  }
}
