/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.deployment.transform.BpmnValidator;

public final class BpmnFactory {

  public static BpmnTransformer createTransformer() {
    return new BpmnTransformer(createExpressionLanguage());
  }

  public static BpmnValidator createValidator(final ExpressionProcessor expressionProcessor) {
    return new BpmnValidator(createExpressionLanguage(), expressionProcessor);
  }

  private static ExpressionLanguage createExpressionLanguage() {
    return ExpressionLanguageFactory.createExpressionLanguage();
  }
}
