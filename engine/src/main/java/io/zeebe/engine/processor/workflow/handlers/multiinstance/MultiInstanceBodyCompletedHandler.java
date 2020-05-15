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
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MultiInstanceBodyCompletedHandler extends AbstractMultiInstanceBodyHandler {

  private final BpmnStepHandler multiInstanceBodyHandler;

  private final MsgPackReader variableReader = new MsgPackReader();
  private final MsgPackWriter variableWriter = new MsgPackWriter();

  private final ExpandableArrayBuffer variableBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer resultBuffer = new UnsafeBuffer(0, 0);

  public MultiInstanceBodyCompletedHandler(
      final Function<BpmnStep, BpmnStepHandler> handlerLookup,
      final Consumer<BpmnStepContext<?>> innerHandler,
      final ExpressionProcessor expressionProcessor) {
    super(null, innerHandler, expressionProcessor);
    multiInstanceBodyHandler = handlerLookup.apply(BpmnStep.FLOWOUT_ELEMENT_COMPLETED);
  }

  @Override
  protected void handleInnerActivity(final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final var loopCharacteristics = context.getElement().getLoopCharacteristics();

    if (loopCharacteristics.isSequential()) {

      final var inputCollectionVariable = readInputCollectionVariable(context);
      if (inputCollectionVariable.isEmpty()) {
        return;
      }

      final var array = inputCollectionVariable.get();
      final var loopCounter = context.getFlowScopeInstance().getMultiInstanceLoopCounter();
      if (loopCounter < array.size()) {

        final var item = array.get(loopCounter);
        createInnerInstance(context, context.getFlowScopeInstance().getKey(), item);
      }
    }

    final Optional<Boolean> updatedSuccessfully =
        loopCharacteristics
            .getOutputCollection()
            .map(variableName -> updateOutputCollection(context, variableName));

    if (updatedSuccessfully.isPresent() && !updatedSuccessfully.get()) {
      // An incident was raised while updating the output collection, stop handling activity
      return;
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

  private boolean updateOutputCollection(
      final BpmnStepContext<ExecutableMultiInstanceBody> context, final DirectBuffer variableName) {

    final var variablesState = context.getElementInstanceState().getVariablesState();
    final var bodyInstanceKey = context.getFlowScopeInstance().getKey();
    final var workflowKey = context.getValue().getWorkflowKey();
    final var loopCounter = context.getElementInstance().getMultiInstanceLoopCounter();

    final Optional<DirectBuffer> elementVariable = readOutputElementVariable(context);
    if (elementVariable.isEmpty()) {
      return false;
    }

    // we need to read the output element variable before the current collection is read,
    // because readOutputElementVariable(Context) uses the same buffer as getVariableLocal
    // this could also be avoided by cloning the current collection, but that is slower.
    final var currentCollection = variablesState.getVariableLocal(bodyInstanceKey, variableName);
    final var updatedCollection = insertAt(currentCollection, loopCounter, elementVariable.get());
    variablesState.setVariableLocal(bodyInstanceKey, workflowKey, variableName, updatedCollection);
    return true;
  }

  private Optional<DirectBuffer> readOutputElementVariable(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final var expression =
        context.getElement().getLoopCharacteristics().getOutputElement().orElseThrow();
    return expressionProcessor.evaluateAnyExpression(expression, context);
  }

  private DirectBuffer insertAt(
      final DirectBuffer array, final int index, final DirectBuffer element) {

    variableReader.wrap(array, 0, array.capacity());
    variableReader.readArrayHeader();
    variableReader.skipValues((long) index - 1L);

    final var offsetBefore = variableReader.getOffset();
    variableReader.skipValue();
    final var offsetAfter = variableReader.getOffset();

    variableWriter.wrap(variableBuffer, 0);
    variableWriter.writeRaw(array, 0, offsetBefore);
    variableWriter.writeRaw(element);
    variableWriter.writeRaw(array, offsetAfter, array.capacity() - offsetAfter);

    final var length = variableWriter.getOffset();

    resultBuffer.wrap(variableBuffer, 0, length);
    return resultBuffer;
  }
}
