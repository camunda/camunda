/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.common;

import io.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.analyzers.CatchEventAnalyzer;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ErrorEventHandler {

  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final EventHandle eventHandle;
  private final CatchEventAnalyzer stateAnalyzer;

  public ErrorEventHandler(
      final ProcessState processState,
      final ElementInstanceState elementInstanceState,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final KeyGenerator keyGenerator) {

    eventHandle = new EventHandle(keyGenerator, eventScopeInstanceState);
    stateAnalyzer = new CatchEventAnalyzer(processState, elementInstanceState);
  }

  /**
   * Throw an error event. The event is propagated from the given instance through the scope
   * hierarchy until the event is caught by a catch event. The event is only thrown if a catch event
   * was found.
   *
   * @param errorCode the error code of the error event
   * @param instance the instance there the event propagation starts
   * @param eventWriter the writer to be used for writing the followup event
   * @return {@code true} if the error event is thrown and caught by an catch event
   */
  public boolean throwErrorEvent(
      final DirectBuffer errorCode,
      final ElementInstance instance,
      final TypedEventWriter eventWriter) {

    final var foundCatchEvent = stateAnalyzer.findCatchEvent(errorCode, instance);
    if (foundCatchEvent != null) {

      eventHandle.triggerEvent(
          eventWriter,
          foundCatchEvent.getElementInstance(),
          foundCatchEvent.getCatchEvent(),
          NO_VARIABLES);

      return true;
    }

    return false;
  }
}
