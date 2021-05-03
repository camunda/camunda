/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.analyzers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.instance.ElementInstance;
import org.agrona.DirectBuffer;

/**
 * Helper class that analyzes a process instance at runtime. It provides information about the
 * existence of catch events. The information is derived from {@link ProcessState} and {@link
 * ElementInstanceState}.
 */
public final class CatchEventAnalyzer {

  private final CatchEventTuple catchEventTuple = new CatchEventTuple();

  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;

  public CatchEventAnalyzer(
      final ProcessState processState, final ElementInstanceState elementInstanceState) {
    this.processState = processState;
    this.elementInstanceState = elementInstanceState;
  }

  public boolean hasCatchEvent(final DirectBuffer errorCode, final ElementInstance instance) {
    return findCatchEvent(errorCode, instance) != null;
  }

  public CatchEventTuple findCatchEvent(final DirectBuffer errorCode, ElementInstance instance) {
    // assuming that error events are used rarely
    // - just walk through the scope hierarchy and look for a matching catch event

    while (instance != null && instance.isActive()) {
      final var instanceRecord = instance.getValue();
      final var process = getProcess(instanceRecord.getProcessDefinitionKey());

      final var found = findCatchEventInProcess(errorCode, process, instance);
      if (found != null) {
        return found;
      }

      // find in parent process instance if exists
      final var parentElementInstanceKey = instanceRecord.getParentElementInstanceKey();
      instance = elementInstanceState.getInstance(parentElementInstanceKey);
    }

    // no matching catch event found
    return null;
  }

  private CatchEventTuple findCatchEventInProcess(
      final DirectBuffer errorCode, final ExecutableProcess process, ElementInstance instance) {

    while (instance != null && instance.isActive() && !instance.isInterrupted()) {
      final var found = findCatchEventInScope(errorCode, process, instance);
      if (found != null) {
        return found;
      }

      // find in parent scope if exists
      final var instanceParentKey = instance.getParentKey();
      instance = elementInstanceState.getInstance(instanceParentKey);
    }

    return null;
  }

  private CatchEventTuple findCatchEventInScope(
      final DirectBuffer errorCode,
      final ExecutableProcess process,
      final ElementInstance instance) {

    final var processInstanceRecord = instance.getValue();
    final var elementId = processInstanceRecord.getElementIdBuffer();
    final var elementType = processInstanceRecord.getBpmnElementType();

    final var element = process.getElementById(elementId, elementType, ExecutableActivity.class);

    for (final ExecutableCatchEvent catchEvent : element.getEvents()) {
      if (hasErrorCode(catchEvent, errorCode)) {

        catchEventTuple.instance = instance;
        catchEventTuple.catchEvent = catchEvent;
        return catchEventTuple;
      }
    }

    return null;
  }

  private boolean hasErrorCode(
      final ExecutableCatchEvent catchEvent, final DirectBuffer errorCode) {
    return catchEvent.isError() && catchEvent.getError().getErrorCode().equals(errorCode);
  }

  private ExecutableProcess getProcess(final long processDefinitionKey) {

    final var deployedProcess = processState.getProcessByKey(processDefinitionKey);
    if (deployedProcess == null) {
      throw new IllegalStateException(
          String.format(
              "Expected process with key '%d' to be deployed but not found", processDefinitionKey));
    }

    return deployedProcess.getProcess();
  }

  public static final class CatchEventTuple {
    private ExecutableCatchEvent catchEvent;
    private ElementInstance instance;

    public ExecutableCatchEvent getCatchEvent() {
      return catchEvent;
    }

    public ElementInstance getElementInstance() {
      return instance;
    }
  }
}
