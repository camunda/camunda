/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.multiinstance;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.BpmnStepHandler;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.buffer.BufferUtil;
import java.util.function.Function;

public class MultiInstanceBodyActivatingHandler extends AbstractMultiInstanceBodyHandler {

  private final CatchEventSubscriber catchEventSubscriber;

  public MultiInstanceBodyActivatingHandler(
      final Function<BpmnStep, BpmnStepHandler> innerHandlerLookup,
      final CatchEventSubscriber catchEventSubscriber) {
    super(WorkflowInstanceIntent.ELEMENT_ACTIVATED, innerHandlerLookup);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {

    final MsgPackQueryProcessor.QueryResults results = readInputCollectionVariable(context);

    if (results.size() == 0) {
      context.raiseIncident(
          ErrorType.EXTRACT_VALUE_ERROR,
          String.format(
              "Expected multi-instance input collection variable '%s' to be an ARRAY, but not found.",
              getInputCollectionVariableName(context)));
      return false;

    } else if (!results.getSingleResult().isArray()) {
      context.raiseIncident(
          ErrorType.EXTRACT_VALUE_ERROR,
          String.format(
              "Expected multi-instance input collection variable '%s' to be an ARRAY, but found '%s'.",
              getInputCollectionVariableName(context), results.getSingleResult().getType()));
      return false;
    }

    catchEventSubscriber.subscribeToEvents(context);

    return true;
  }

  private String getInputCollectionVariableName(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    return BufferUtil.bufferAsString(
        context.getElement().getLoopCharacteristics().getInputCollection().getVariableName());
  }
}
