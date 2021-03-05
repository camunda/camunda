/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;

public class ExecutableActivity extends ExecutableFlowNode implements ExecutableCatchEventSupplier {

  private final List<ExecutableBoundaryEvent> boundaryEvents = new ArrayList<>();
  private final List<ExecutableFlowElementContainer> eventSubprocesses = new ArrayList<>();

  private final List<ExecutableCatchEvent> catchEvents = new ArrayList<>();
  private final List<DirectBuffer> interruptingIds = new ArrayList<>();

  public ExecutableActivity(final String id) {
    super(id);
  }

  public void attach(final ExecutableBoundaryEvent boundaryEvent) {
    boundaryEvents.add(boundaryEvent);
    catchEvents.add(boundaryEvent);

    if (boundaryEvent.interrupting()) {
      interruptingIds.add(boundaryEvent.getId());
    }
  }

  public void attach(final ExecutableFlowElementContainer eventSubprocess) {
    eventSubprocesses.add(eventSubprocess);

    final var startEvent = eventSubprocess.getStartEvents().get(0);
    catchEvents.add(0, startEvent);

    if (startEvent.interrupting()) {
      interruptingIds.add(startEvent.getId());
    }
  }

  @Override
  public List<ExecutableCatchEvent> getEvents() {
    // the order defines the precedence
    // 1. event subprocesses
    // 2. boundary events
    return catchEvents;
  }

  @Override
  public Collection<DirectBuffer> getInterruptingElementIds() {
    return interruptingIds;
  }

  public List<ExecutableBoundaryEvent> getBoundaryEvents() {
    return boundaryEvents;
  }

  public List<ExecutableFlowElementContainer> getEventSubprocesses() {
    return eventSubprocesses;
  }
}
