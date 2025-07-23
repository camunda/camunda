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
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.agrona.DirectBuffer;

public class MultiInstanceInputCollectionBehavior {
  private final ExpressionProcessor expressionProcessor;
  private final BpmnStateBehavior stateBehavior;

  public MultiInstanceInputCollectionBehavior(
      final ExpressionProcessor expressionProcessor, final BpmnStateBehavior stateBehavior) {
    this.expressionProcessor = expressionProcessor;
    this.stateBehavior = stateBehavior;
  }

  public Either<Failure, List<DirectBuffer>> initializeInputCollection(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final var inputCollectionExpression = element.getLoopCharacteristics().getInputCollection();
    // TODO write the event before returning the input collection
    return expressionProcessor.evaluateArrayExpression(
        inputCollectionExpression, context.getElementInstanceKey());
  }

  public Either<Failure, List<DirectBuffer>> getInputCollection() {
    // TODO read input collection from state
    return Either.right(List.of());
  }
}
