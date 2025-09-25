/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableCalledDecision;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;

public final class CalledDecisionTransformer {

  public void transform(
      final ExecutableCalledDecision executableElement,
      final TransformContext context,
      final ZeebeCalledDecision calledDecision) {

    if (calledDecision == null) {
      return;
    }

    final var expressionLanguage = context.getExpressionLanguage();

    final var decisionIdExpression =
        expressionLanguage.parseExpression(calledDecision.getDecisionId());
    executableElement.setDecisionId(decisionIdExpression);

    final var resultVariable = calledDecision.getResultVariable();
    executableElement.setResultVariable(resultVariable);

    final var bindingType = calledDecision.getBindingType();
    executableElement.setBindingType(bindingType);

    final var versionTag = calledDecision.getVersionTag();
    executableElement.setVersionTag(versionTag);
  }
}
