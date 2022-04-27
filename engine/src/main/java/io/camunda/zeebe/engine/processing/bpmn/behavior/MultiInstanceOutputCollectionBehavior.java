/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackToken;
import io.camunda.zeebe.msgpack.spec.MsgPackType;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MultiInstanceOutputCollectionBehavior {

  private final MsgPackReader outputCollectionReader = new MsgPackReader();
  private final MsgPackWriter outputCollectionWriter = new MsgPackWriter();
  private final ExpandableArrayBuffer outputCollectionBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer updatedOutputCollectionBuffer = new UnsafeBuffer(0, 0);

  private final BpmnStateBehavior stateBehavior;
  private final ExpressionProcessor expressionProcessor;

  MultiInstanceOutputCollectionBehavior(
      final BpmnStateBehavior stateBehavior, final ExpressionProcessor expressionProcessor) {
    this.stateBehavior = stateBehavior;
    this.expressionProcessor = expressionProcessor;
  }

  public void initializeOutputCollection(
      final BpmnElementContext context, final DirectBuffer variableName, final int size) {

    outputCollectionWriter.wrap(outputCollectionBuffer, 0);

    // initialize the array with nil
    outputCollectionWriter.writeArrayHeader(size);
    for (var i = 0; i < size; i++) {
      outputCollectionWriter.writeNil();
    }

    final var length = outputCollectionWriter.getOffset();

    stateBehavior.setLocalVariable(context, variableName, outputCollectionBuffer, 0, length);
  }

  public Either<Failure, Void> updateOutputCollection(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext childContext,
      final BpmnElementContext flowScopeContext) {

    return element
        .getLoopCharacteristics()
        .getOutputCollection()
        .map(
            variableName ->
                updateOutputCollection(element, childContext, flowScopeContext, variableName))
        .orElse(Either.right(null));
  }

  private Either<Failure, Void> updateOutputCollection(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext childContext,
      final BpmnElementContext flowScopeContext,
      final DirectBuffer variableName) {

    final var loopCounter =
        stateBehavior.getElementInstance(childContext).getMultiInstanceLoopCounter();

    return readOutputElementVariable(element, childContext)
        .flatMap(
            elementVariable -> {
              // we need to read the output element variable before the current collection
              // is read, because readOutputElementVariable(Context) uses the same
              // buffer as getVariableLocal this could also be avoided by cloning the current
              // collection, but that is slower.
              final var currentCollection =
                  stateBehavior.getLocalVariable(flowScopeContext, variableName);
              return replaceAt(
                      currentCollection,
                      loopCounter,
                      elementVariable,
                      flowScopeContext.getElementInstanceKey(),
                      variableName)
                  .map(
                      updatedCollection -> {
                        stateBehavior.setLocalVariable(
                            flowScopeContext, variableName, updatedCollection);
                        return null;
                      });
            });
  }

  private Either<Failure, DirectBuffer> readOutputElementVariable(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final var expression = element.getLoopCharacteristics().getOutputElement().orElseThrow();
    return expressionProcessor.evaluateAnyExpression(expression, context.getElementInstanceKey());
  }

  private Either<Failure, DirectBuffer> replaceAt(
      final DirectBuffer array,
      final int index,
      final DirectBuffer element,
      final long variableScopeKey,
      final DirectBuffer variableName) {

    outputCollectionReader.wrap(array, 0, array.capacity());
    final var token = outputCollectionReader.readToken();

    final var optValidationFailure =
        validateIsCollectionAndHasAppropriateSIze(index, variableScopeKey, variableName, token);
    if (optValidationFailure.isPresent()) {
      return Either.left(optValidationFailure.get());
    }

    outputCollectionReader.skipValues((long) index - 1L);

    final var offsetBefore = outputCollectionReader.getOffset();
    outputCollectionReader.skipValue();
    final var offsetAfter = outputCollectionReader.getOffset();

    outputCollectionWriter.wrap(outputCollectionBuffer, 0);
    outputCollectionWriter.writeRaw(array, 0, offsetBefore);
    outputCollectionWriter.writeRaw(element);
    outputCollectionWriter.writeRaw(array, offsetAfter, array.capacity() - offsetAfter);

    final var length = outputCollectionWriter.getOffset();

    updatedOutputCollectionBuffer.wrap(outputCollectionBuffer, 0, length);
    return Either.right(updatedOutputCollectionBuffer);
  }

  private Optional<Failure> validateIsCollectionAndHasAppropriateSIze(
      final int index,
      final long variableScopeKey,
      final DirectBuffer variableName,
      final MsgPackToken token) {
    if (token.getType() != MsgPackType.ARRAY) {
      return Optional.of(
          new Failure(
              String.format(
                  "Unable to update an item in output collection '%s' because the type of the output collection is: %s. This may happen when multiple BPMN elements write to the same variable.",
                  bufferAsString(variableName), token.getType()),
              ErrorType.EXTRACT_VALUE_ERROR,
              variableScopeKey));
    }

    final int size = token.getSize();
    if (index > size) {
      return Optional.of(
          new Failure(
              String.format(
                  "Unable to update an item in output collection '%s' at position %d because the size of the collection is: %d. This may happen when multiple BPMN elements write to the same variable.",
                  bufferAsString(variableName), index, size),
              ErrorType.EXTRACT_VALUE_ERROR,
              variableScopeKey));
    }
    return Optional.empty();
  }
}
