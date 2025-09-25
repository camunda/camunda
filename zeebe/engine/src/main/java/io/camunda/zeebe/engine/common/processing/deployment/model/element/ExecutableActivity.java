/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;

public class ExecutableActivity extends ExecutableFlowNode implements ExecutableCatchEventSupplier {

  private final List<ExecutableBoundaryEvent> boundaryEvents = new ArrayList<>();
  private final List<ExecutableFlowElementContainer> eventSubprocesses = new ArrayList<>();

  private final List<ExecutableCatchEvent> catchEvents = new ArrayList<>();
  private final List<DirectBuffer> interruptingIds = new ArrayList<>();
  private final List<DirectBuffer> boundaryElementIds = new ArrayList<>();

  public ExecutableActivity(final String id) {
    super(id);
  }

  public void attach(final ExecutableBoundaryEvent boundaryEvent) {
    boundaryEvents.add(boundaryEvent);
    catchEvents.add(boundaryEvent);

    final var boundaryEventElementId = boundaryEvent.getId();
    boundaryElementIds.add(boundaryEventElementId);

    if (boundaryEvent.interrupting()) {
      interruptingIds.add(boundaryEventElementId);
    }
  }

  public void attach(final ExecutableFlowElementContainer eventSubprocess) {
    eventSubprocesses.add(eventSubprocess);

    final var startEvent = eventSubprocess.getStartEvents().getFirst();
    catchEvents.addFirst(startEvent);

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

  @Override
  public Collection<DirectBuffer> getBoundaryElementIds() {
    return boundaryElementIds;
  }

  public List<ExecutableBoundaryEvent> getBoundaryEvents() {
    return boundaryEvents;
  }

  public List<ExecutableFlowElementContainer> getEventSubprocesses() {
    return eventSubprocesses;
  }
}
