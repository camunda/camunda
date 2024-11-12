/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

/**
 * ExecutableFlowElementContainer is currently used to represent processes as well ({@link
 * io.camunda.zeebe.model.bpmn.instance.Process}), which may seem counter intuitive; at the moment,
 * the reason is that sub processes are also modelled using the same class, and sub processes need
 * to reuse the logic for both. As this diverges (i.e. processes/sub-processes), we should refactor
 * this.
 */
public class ExecutableFlowElementContainer extends ExecutableActivity {

  private final List<ExecutableStartEvent> startEvents = new ArrayList<>();
  private final Map<DirectBuffer, AbstractFlowElement> childElements = new HashMap<>();

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
    startEvents.add(startEvent);
  }

  public boolean hasNoneStartEvent() {
    return startEvents.stream().anyMatch(ExecutableCatchEventElement::isNone);
  }

  public boolean hasMessageStartEvent() {
    return startEvents.stream().anyMatch(ExecutableCatchEventElement::isMessage);
  }

  public boolean hasTimerStartEvent() {
    return startEvents.stream().anyMatch(ExecutableCatchEventElement::isTimer);
  }

  public boolean hasSignalStartEvent() {
    return startEvents.stream().anyMatch(ExecutableCatchEventElement::isSignal);
  }

  public void addChildElement(final AbstractFlowElement element) {
    childElements.put(element.getId(), element);
  }

  public Collection<AbstractFlowElement> getChildElements() {
    return childElements.values();
  }
}
