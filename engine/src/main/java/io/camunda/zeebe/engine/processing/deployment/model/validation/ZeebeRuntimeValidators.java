/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ZeebeExpressionValidator.ExpressionVerification;
import io.camunda.zeebe.model.bpmn.instance.ConditionExpression;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAssignmentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskSchedule;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public final class ZeebeRuntimeValidators {

  public static Collection<ModelElementValidator<?>> getValidators(
      final ExpressionLanguage expressionLanguage, final ExpressionProcessor expressionProcessor) {
    return List.of(
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeInput.class)
            .hasValidExpression(ZeebeInput::getSource, ExpressionVerification::isMandatory)
            .hasValidPath(ZeebeInput::getTarget)
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeOutput.class)
            .hasValidExpression(
                ZeebeOutput::getSource, expression -> expression.isNonStatic().isMandatory())
            .hasValidPath(ZeebeOutput::getTarget)
            .build(expressionLanguage),
        ZeebeExpressionValidator.verifyThat(Message.class)
            .hasValidExpression(Message::getName, ExpressionVerification::isOptional)
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
            .hasValidExpression(ZeebeTaskDefinition::getType, ExpressionVerification::isMandatory)
            .hasValidExpression(
                ZeebeTaskDefinition::getRetries, ExpressionVerification::isMandatory)
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeCalledElement.class)
            .hasValidExpression(
                ZeebeCalledElement::getProcessId, ExpressionVerification::isMandatory)
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
        ZeebeExpressionValidator.verifyThat(ZeebeAssignmentDefinition.class)
            .hasValidExpression(
                ZeebeAssignmentDefinition::getAssignee, ExpressionVerification::isOptional)
            .hasValidExpression(
                ZeebeAssignmentDefinition::getCandidateGroups,
                expression ->
                    expression
                        .isOptional()
                        .satisfiesIfStatic(
                            ZeebeExpressionValidator::isListOfCsv,
                            "be a list of comma-separated values, e.g. 'a,b,c'"))
            .hasValidExpression(
                ZeebeAssignmentDefinition::getCandidateUsers,
                expression ->
                    expression
                        .isOptional()
                        .satisfiesIfStatic(
                            ZeebeExpressionValidator::isListOfCsv,
                            "be a list of comma-separated values, e.g. 'a,b,c'"))
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeTaskSchedule.class)
            .hasValidExpression(
                ZeebeTaskSchedule::getDueDate,
                expression ->
                    expression
                        .isOptional()
                        .satisfiesIfStatic(
                            staticExpression ->
                                ZeebeExpressionValidator.isValidDateTime(
                                    staticExpression, expressionProcessor),
                            "be a valid DateTime String, e.g. '2023-03-02T15:35+02:00'"))
            .hasValidExpression(
                ZeebeTaskSchedule::getFollowUpDate,
                expression ->
                    expression
                        .isOptional()
                        .satisfiesIfStatic(
                            staticExpression ->
                                ZeebeExpressionValidator.isValidDateTime(
                                    staticExpression, expressionProcessor),
                            "be a valid DateTime String, e.g. '2023-03-02T15:35+02:00'"))
            .build(expressionLanguage),
        // ----------------------------------------
        new TimerCatchEventExpressionValidator(expressionLanguage, expressionProcessor),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeCalledDecision.class)
            .hasValidExpression(
                ZeebeCalledDecision::getDecisionId, ExpressionVerification::isMandatory)
            .build(expressionLanguage),
        // ----------------------------------------
        new ZeebeTaskHeadersValidator(),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(MultiInstanceLoopCharacteristics.class)
            .hasValidExpression(
                loopCharacteristics ->
                    loopCharacteristics.getCompletionCondition() != null
                        ? loopCharacteristics.getCompletionCondition().getTextContent()
                        : null,
                ExpressionVerification::isOptional)
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(ZeebeScript.class)
            .hasValidExpression(
                ZeebeScript::getExpression, expression -> expression.isNonStatic().isMandatory())
            .build(expressionLanguage),
        // ----------------------------------------
        ZeebeExpressionValidator.verifyThat(Signal.class)
            .hasValidExpression(Signal::getName, ExpressionVerification::isOptional)
            .build(expressionLanguage),
        // Checks signal name expressions of start event signals
        new ProcessSignalStartEventSignalNameValidator(expressionLanguage),
        // Check that unsupported signal boundary events cannot be deployed
        new UnsupportedSignalBoundaryEventValidator(),
        // Check that unsupported signal intermediate catch events cannot be deployed
        new UnsupportedSignalIntermediateCatchEventValidator(),
        // Check that unsupported signal event subprocess cannot be deployed
        new UnsupportedSignalEventSubprocessValidator());
  }
}
