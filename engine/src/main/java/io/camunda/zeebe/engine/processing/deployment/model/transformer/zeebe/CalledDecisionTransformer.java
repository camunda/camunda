/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;

public final class CalledDecisionTransformer {

  public void transform(
      final ExecutableBusinessRuleTask businessRuleTask, final ZeebeCalledDecision calledDecision) {

    if (calledDecision == null) {
      return;
    }

    final var decisionId = calledDecision.getDecisionId();
    businessRuleTask.setDecisionId(decisionId);

    final var resultVariable = calledDecision.getResultVariable();
    businessRuleTask.setResultVariable(resultVariable);
  }
}
