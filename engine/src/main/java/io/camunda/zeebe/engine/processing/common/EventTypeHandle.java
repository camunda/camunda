/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.protocol.record.value.EventType;

public class EventTypeHandle {

  public static EventType getEventType(final ExecutableFlowElement element) {
    EventType eventType = EventType.UNSPECIFIED;

    if (element instanceof ExecutableCatchEvent) {
      if (((ExecutableCatchEvent) element).isNone()) {
        eventType = EventType.NONE;
      } else if (((ExecutableCatchEvent) element).isMessage()) {
        eventType = EventType.MESSAGE;
      } else if (((ExecutableCatchEvent) element).isTimer()) {
        eventType = EventType.TIMER;
      } else if (((ExecutableCatchEvent) element).isError()) {
        eventType = EventType.ERROR;
      }
    } else if (element instanceof ExecutableEndEvent) {
      if (((ExecutableEndEvent) element).isNoneEndEvent()) {
        eventType = EventType.NONE;
      } else if (((ExecutableEndEvent) element).isMessageEventEvent()) {
        eventType = EventType.MESSAGE;
      } else if (((ExecutableEndEvent) element).isTerminateEndEvent()) {
        eventType = EventType.TERMINATE;
      } else if (((ExecutableEndEvent) element).isErrorEndEvent()) {
        eventType = EventType.ERROR;
      }
    }

    return eventType;
  }
}
