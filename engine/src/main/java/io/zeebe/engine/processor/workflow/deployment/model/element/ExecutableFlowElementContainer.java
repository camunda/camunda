/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import java.util.ArrayList;
import java.util.List;

/**
 * ExecutableFlowElementContainer is currently used to represent processes as well ({@link
 * io.zeebe.model.bpmn.instance.Process}), which may seem counter intuitive; at the moment, the
 * reason is that sub processes are also modelled using the same class, and sub processes need to
 * reuse the logic for both. As this diverges (i.e. processes/sub-processes), we should refactor
 * this.
 */
public class ExecutableFlowElementContainer extends ExecutableActivity {

  private final List<ExecutableStartEvent> startEvents = new ArrayList<>();

  public ExecutableFlowElementContainer(final String id) {
    super(id);
  }

  public ExecutableStartEvent getNoneStartEvent() {
    for (final ExecutableStartEvent startEvent : startEvents) {
      if (startEvent.isNone()) {
        return startEvent;
      }
    }
    return null;
  }

  public List<ExecutableStartEvent> getStartEvents() {
    return startEvents;
  }

  public void addStartEvent(final ExecutableStartEvent startEvent) {
    this.startEvents.add(startEvent);
  }
}
