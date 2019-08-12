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
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public class MultiInstanceBodyActivatedHandler extends AbstractMultiInstanceBodyHandler {

  private final ExpandableArrayBuffer variableBuffer = new ExpandableArrayBuffer();
  private final MsgPackWriter variableWriter = new MsgPackWriter();

  public MultiInstanceBodyActivatedHandler(
      final Function<BpmnStep, BpmnStepHandler> innerHandlerLookup) {
    super(WorkflowInstanceIntent.ELEMENT_COMPLETING, innerHandlerLookup);
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {

    final var loopCharacteristics = context.getElement().getLoopCharacteristics();
    final var array = readInputCollectionVariable(context).getSingleResult().getArray();

    loopCharacteristics
        .getOutputCollection()
        .ifPresent(variableName -> initializeOutputCollection(context, variableName, array.size()));

    if (array.isEmpty()) {
      // complete the multi-instance body immediately
      return true;
    }

    if (loopCharacteristics.isSequential()) {
      final var firstItem = array.getElement(0);
      createInnerInstance(context, context.getKey(), firstItem);

    } else {
      array.forEach(item -> createInnerInstance(context, context.getKey(), item));
    }

    return false;
  }

  private void initializeOutputCollection(
      final BpmnStepContext<ExecutableMultiInstanceBody> context,
      final DirectBuffer variableName,
      final int size) {

    variableWriter.wrap(variableBuffer, 0);

    // initialize the array with nil
    variableWriter.writeArrayHeader(size);
    for (var i = 0; i < size; i++) {
      variableWriter.writeNil();
    }

    final var length = variableWriter.getOffset();

    context
        .getElementInstanceState()
        .getVariablesState()
        .setVariableLocal(
            context.getKey(),
            context.getValue().getWorkflowKey(),
            variableName,
            variableBuffer,
            0,
            length);
  }
}
