/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.multiinstance.MultiInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.MultiInstanceIntent;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.agrona.DirectBuffer;

public class MultiInstanceInputCollectionBehavior {
  private final ExpressionProcessor expressionProcessor;
  private final BpmnStateBehavior stateBehavior;
  private final StateWriter stateWriter;

  public MultiInstanceInputCollectionBehavior(
      final ExpressionProcessor expressionProcessor,
      final BpmnStateBehavior stateBehavior,
      final StateWriter stateWriter) {
    this.expressionProcessor = expressionProcessor;
    this.stateBehavior = stateBehavior;
    this.stateWriter = stateWriter;
  }

  public Either<Failure, List<DirectBuffer>> initializeInputCollection(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    return evaluateInputCollection(element, context)
        .map(
            inputCollection -> {
              final var record = new MultiInstanceRecord().setInputCollection(inputCollection);
              stateWriter.appendFollowUpEvent(
                  context.getElementInstanceKey(),
                  MultiInstanceIntent.INPUT_COLLECTION_EVALUATED,
                  record);
              return inputCollection;
            });
  }

  public Either<Failure, List<DirectBuffer>> getInputCollection(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    return stateBehavior
        .getInputCollection(context.getElementInstanceKey())
        .map(Either::<Failure, List<DirectBuffer>>right)
        .orElseGet(() -> evaluateInputCollection(element, context));
  }

  private Either<Failure, List<DirectBuffer>> evaluateInputCollection(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final var inputCollectionExpression = element.getLoopCharacteristics().getInputCollection();
    return expressionProcessor.evaluateArrayExpression(
        inputCollectionExpression, context.getElementInstanceKey(), context.getTenantId());
  }
}
