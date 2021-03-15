/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.validation;

import io.zeebe.el.EvaluationResult;
import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ResultType;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

/**
 * This class validates that the message names of messages associated with a start event can be
 * evaluated without a context (that is, the expressions do not refer to variables) and evaluate to
 * a string
 */
final class ProcessMessageStartEventMessageNameValidator
    implements ModelElementValidator<StartEvent> {

  private final ExpressionLanguage expressionLanguage;

  ProcessMessageStartEventMessageNameValidator(final ExpressionLanguage expressionLanguage) {
    this.expressionLanguage = expressionLanguage;
  }

  @Override
  public Class<StartEvent> getElementType() {
    return StartEvent.class;
  }

  @Override
  public void validate(
      final StartEvent element, final ValidationResultCollector validationResultCollector) {
    if (element.getScope() instanceof Process) {
      element.getEventDefinitions().stream()
          .filter(MessageEventDefinition.class::isInstance)
          .map(MessageEventDefinition.class::cast)
          .forEach(definition -> validateMessageName(definition, validationResultCollector));
    }
  }

  private void validateMessageName(
      final MessageEventDefinition messageEventDefinition,
      final ValidationResultCollector resultCollector) {
    final String nameExpression = messageEventDefinition.getMessage().getName();
    final Expression parseResult = expressionLanguage.parseExpression(nameExpression);

    final EvaluationResult evaluationResult =
        expressionLanguage.evaluateExpression(parseResult, var -> null);

    if (evaluationResult.isFailure()) {
      resultCollector.addError(
          0,
          String.format(
              "Expected constant expression but found '%s', which could not be evaluated without context: %s",
              nameExpression, evaluationResult.getFailureMessage()));
    } else if (evaluationResult.getType() != ResultType.STRING) {
      resultCollector.addError(
          0,
          String.format(
              "Expected constant expression of type String for message name '%s', but was %s",
              nameExpression, evaluationResult.getType()));
    }
  }
}
