/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableScriptTask;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;

public final class ScriptTransformer {

  public void transform(
      final ExecutableScriptTask executableElement,
      final TransformContext context,
      final ZeebeScript zeebeScript) {

    if (zeebeScript == null) {
      return;
    }

    final var expressionLanguage = context.getExpressionLanguage();

    final var scriptExpression = expressionLanguage.parseExpression(zeebeScript.getExpression());
    executableElement.setExpression(scriptExpression);

    final var resultVariable = zeebeScript.getResultVariable();
    executableElement.setResultVariable(resultVariable);
  }
}
