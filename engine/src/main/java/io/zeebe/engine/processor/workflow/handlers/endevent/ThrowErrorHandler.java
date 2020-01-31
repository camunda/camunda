/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.endevent;

import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableEndEvent;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatedHandler;
import io.zeebe.protocol.record.value.ErrorType;
import org.agrona.DirectBuffer;

public final class ThrowErrorHandler extends ElementActivatedHandler<ExecutableEndEvent> {

  private final ErrorEventHandler errorEventHandler;

  public ThrowErrorHandler(final ErrorEventHandler errorEventHandler) {
    super(null);
    this.errorEventHandler = errorEventHandler;
  }

  @Override
  protected boolean handleState(final BpmnStepContext<ExecutableEndEvent> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final var endEvent = context.getElement();
    final var error = endEvent.getError();
    ensureNotNull("error", error);

    final var errorCode = error.getErrorCode();
    ensureNotNullOrEmpty("errorCode", errorCode);

    // the error can be caught at the parent or an upper scope
    final var flowScopeInstance = context.getFlowScopeInstance();
    final var streamWriter = context.getOutput().getStreamWriter();

    final var errorThrown =
        errorEventHandler.throwErrorEvent(errorCode, flowScopeInstance, streamWriter);

    if (!errorThrown) {
      raiseIncident(context, errorCode);
    }

    return errorThrown;
  }

  private void raiseIncident(
      final BpmnStepContext<ExecutableEndEvent> context, final DirectBuffer errorCode) {

    final var errorMessage =
        String.format(
            "An error was thrown with the code '%s' but not caught.", bufferAsString(errorCode));

    context.raiseIncident(ErrorType.UNHANDLED_ERROR_EVENT, errorMessage);
  }
}
