/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;

public class ExecutableEventBasedGateway extends ExecutableFlowNode
    implements ExecutableCatchEventSupplier {

  private List<ExecutableCatchEvent> events;
  private List<DirectBuffer> eventIds;

  public ExecutableEventBasedGateway(String id) {
    super(id);
  }

  @Override
  public List<ExecutableCatchEvent> getEvents() {
    return events;
  }

  public void setEvents(List<ExecutableCatchEvent> events) {
    this.events = events;
    this.eventIds = new ArrayList<>(events.size());

    for (final ExecutableCatchEvent event : events) {
      eventIds.add(event.getId());
    }
  }

  @Override
  public Collection<DirectBuffer> getInterruptingElementIds() {
    return eventIds;
  }
}
