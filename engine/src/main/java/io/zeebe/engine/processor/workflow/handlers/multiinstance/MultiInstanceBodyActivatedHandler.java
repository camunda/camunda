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
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.function.Function;
import org.agrona.DirectBuffer;

public class MultiInstanceBodyActivatedHandler extends AbstractMultiInstanceBodyHandler {

  public MultiInstanceBodyActivatedHandler(
      final Function<BpmnStep, BpmnStepHandler> innerHandlerLookup) {
    super(WorkflowInstanceIntent.ELEMENT_COMPLETING, innerHandlerLookup);
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {

    final ArrayResult array = readInputCollectionVariable(context).getSingleResult().getArray();
    if (array.isEmpty()) {
      // complete the multi-instance body immediately
      return true;
    }

    if (context.getElement().getLoopCharacteristics().isSequential()) {

      final DirectBuffer item = array.getElement(0);
      createInnerInstance(context, context.getKey(), item);

      final ElementInstance elementInstance = context.getElementInstance();
      elementInstance.incrementMultiInstanceLoopCounter();
      context.getElementInstanceState().updateInstance(elementInstance);

    } else {
      array.forEach(item -> createInnerInstance(context, context.getKey(), item));
    }

    return false;
  }
}
