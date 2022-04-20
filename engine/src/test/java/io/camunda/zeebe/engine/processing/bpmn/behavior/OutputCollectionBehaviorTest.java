/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableLoopCharacteristics;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OutputCollectionBehaviorTest {

  @Test // regression test for #9143
  void shouldReturnFailureWhenWritingToOutputCollectionOutOfBounds() {
    // given
    final var collectionWithSize1 = createCollection(1);
    final var elementToAdd = wrapString("element to add");
    final var indexThatIsOutOfBounds = 2;
    final var outputElementsExpression = new StaticExpression("OUTPUT_COLLECTION");
    final var outputElementName = wrapString("OUTPUT_ELEMENT");
    final var loopCharacteristics =
        createLoopCharacteristics(outputElementName, outputElementsExpression);
    final var flowScopeContextKey = 12345L;

    final var mockStateBehavior = mock(BpmnStateBehavior.class, Answers.RETURNS_DEEP_STUBS);
    when(mockStateBehavior.getLocalVariable(any(), eq(outputElementName)))
        .thenReturn(collectionWithSize1);

    final var mockExpressionProcessor = mock(ExpressionProcessor.class);
    when(mockExpressionProcessor.evaluateAnyExpression(eq(outputElementsExpression), anyLong()))
        .thenReturn(Either.right(elementToAdd));

    final var mockElement = mock(ExecutableMultiInstanceBody.class);
    when(mockElement.getLoopCharacteristics()).thenReturn(loopCharacteristics);

    final var mockChildContext = mock(BpmnElementContext.class);
    when(mockStateBehavior.getElementInstance(mockChildContext).getMultiInstanceLoopCounter())
        .thenReturn(indexThatIsOutOfBounds);

    final var mockFlowScopeContext = mock(BpmnElementContext.class);
    when(mockFlowScopeContext.getFlowScopeKey()).thenReturn(flowScopeContextKey);

    final var sut = new OutputCollectionBehavior(mockStateBehavior, mockExpressionProcessor);

    // when
    final var result =
        sut.updateOutputCollection(mockElement, mockChildContext, mockFlowScopeContext);

    // then
    assertThat(result.isLeft()).isTrue();

    final var failure = result.getLeft();
    assertThat(failure.getErrorType()).isEqualTo(ErrorType.IO_MAPPING_ERROR);
    assertThat(failure.getMessage())
        .isEqualTo(
            "Unable to update item in output collection 'OUTPUT_ELEMENT' at position 2 because the size of the collection is: 1. This happens when multiple BPMN elements write to the same variable.");
    assertThat(failure.getVariableScopeKey()).isEqualTo(flowScopeContextKey);
  }

  private ExecutableLoopCharacteristics createLoopCharacteristics(
      final DirectBuffer outputElementName, final Expression outputElementExpression) {
    return new ExecutableLoopCharacteristics(
        false,
        Optional.empty(),
        null,
        Optional.empty(),
        Optional.of(outputElementName),
        Optional.of(outputElementExpression));
  }

  private DirectBuffer createCollection(final int size) {
    final var writer = new MsgPackWriter();
    final var buffer = new ExpandableArrayBuffer();

    writer.wrap(buffer, 0);

    // initialize the array with nil
    writer.writeArrayHeader(size);
    for (var i = 0; i < size; i++) {
      writer.writeNil();
    }

    final var length = writer.getOffset();

    return cloneBuffer(buffer, 0, length);
  }
}
