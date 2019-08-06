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
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.ArrayResult;
import java.util.function.Function;
import org.agrona.DirectBuffer;

public class MultiInstanceBodyCompletedHandler extends AbstractMultiInstanceBodyHandler {

  private final BpmnStepHandler multiInstanceBodyHandler;

  public MultiInstanceBodyCompletedHandler(
      final Function<BpmnStep, BpmnStepHandler> innerHandlerLookup) {
    super(null, innerHandlerLookup);

    multiInstanceBodyHandler = innerHandlerLookup.apply(BpmnStep.FLOWOUT_ELEMENT_COMPLETED);
  }

  @Override
  protected void handleInnerActivity(final BpmnStepContext<ExecutableMultiInstanceBody> context) {

    if (context.getElement().getLoopCharacteristics().isSequential()) {

      final ArrayResult array = readInputCollectionVariable(context).getSingleResult().getArray();

      final int loopCounter = context.getFlowScopeInstance().getMultiInstanceLoopCounter();

      if (loopCounter < array.size()) {

        final DirectBuffer item = array.getElement(loopCounter);
        createInnerInstance(context, context.getFlowScopeInstance().getKey(), item);

        final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
        flowScopeInstance.incrementMultiInstanceLoopCounter();
        context.getElementInstanceState().updateInstance(flowScopeInstance);
      }
    }

    // completing the multi-instance body if there are no more tokens
    super.handleInnerActivity(context);
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    multiInstanceBodyHandler.handle(context);
    return true;
  }
}
