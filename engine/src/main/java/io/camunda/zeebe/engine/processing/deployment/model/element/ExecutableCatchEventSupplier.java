/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;

public interface ExecutableCatchEventSupplier extends ExecutableFlowElement {
  List<ExecutableCatchEvent> getEvents();

  /**
   * Returns the ids of the containing elements that interrupt the event scope (e.g. interrupting
   * event subprocesses). An interrupted event scope can not be triggered by other interrupting or
   * non-interrupting events. But the event scope can still be triggered by boundary events.
   */
  Collection<DirectBuffer> getInterruptingElementIds();

  /**
   * Returns the ids of the boundary events. An interrupting boundary event must return its id also
   * with {@link #getInterruptingElementIds()}. Otherwise, it is handled as non-interrupting
   * boundary event.
   */
  Collection<DirectBuffer> getBoundaryElementIds();
}
