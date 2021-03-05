/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.validation;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.deployment.model.validation.ZeebeExpressionValidator.ExpressionVerification;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public final class ZeebeRuntimeValidators {

  public static final Collection<ModelElementValidator<?>> getValidators(
      final ExpressionLanguage expressionLanguage, final ExpressionProcessor expressionProcessor) {
    return List.of(
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeInput.class)
            .hasValidExpression(
                ZeebeInput::getSource, expression -> expression.isNonStatic().isMandatory())
            .hasValidPath(ZeebeInput::getTarget)
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeOutput.class)
            .hasValidExpression(
                ZeebeOutput::getSource, expression -> expression.isNonStatic().isMandatory())
            .hasValidPath(ZeebeOutput::getTarget)
            .build(expressionLanguage),
        ZeebeExpressionValidator.verifyThat(Message.class)
            .hasValidExpression(Message::getName, expression -> expression.isOptional())
            .build(expressionLanguage),
        // Checks message name expressions of start event messages
        new ProcessMessageStartEventMessageNameValidator(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeSubscription.class)
            .hasValidExpression(
                ZeebeSubscription::getCorrelationKey,
                expression -> expression.isNonStatic().isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeLoopCharacteristics.class)
            .hasValidExpression(
                ZeebeLoopCharacteristics::getInputCollection,
                expression -> expression.isNonStatic().isMandatory())
            .hasValidExpression(
                ZeebeLoopCharacteristics::getOutputElement,
                expression -> expression.isNonStatic().isOptional())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ConditionExpression.class)
            .hasValidExpression(
                ConditionExpression::getTextContent,
                expression -> expression.isNonStatic().isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeTaskDefinition.class)
            .hasValidExpression(
                ZeebeTaskDefinition::getType, expression -> expression.isMandatory())
            .hasValidExpression(
                ZeebeTaskDefinition::getRetries, expression -> expression.isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeCalledElement.class)
            .hasValidExpression(
                ZeebeCalledElement::getProcessId, expression -> expression.isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(TimerEventDefinition.class)
            .hasValidExpression(
                definition ->
                    definition.getTimeDate() != null
                        ? definition.getTimeDate().getTextContent()
                        : null,
                ExpressionVerification::isOptional)
            .hasValidExpression(
                definition ->
                    definition.getTimeDuration() != null
                        ? definition.getTimeDuration().getTextContent()
                        : null,
                ExpressionVerification::isOptional)
            .hasValidExpression(
                definition ->
                    definition.getTimeCycle() != null
                        ? definition.getTimeCycle().getTextContent()
                        : null,
                ExpressionVerification::isOptional)
            .build(expressionLanguage),
        // ----------------------------------------
        new TimerCatchEventExpressionValidator(expressionLanguage, expressionProcessor));
  }
}
