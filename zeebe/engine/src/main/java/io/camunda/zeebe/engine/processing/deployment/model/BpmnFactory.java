/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.deployment.transform.BpmnValidator;
import io.camunda.zeebe.engine.processing.deployment.transform.BpmnValidatorConfig;
import java.time.InstantSource;

public final class BpmnFactory {

  public BpmnFactory() {
    /* utility class */
  }

  public static BpmnTransformer createTransformer(
      final InstantSource clock, final ExpressionLanguageMetrics expressionLanguageMetrics) {
    return new BpmnTransformer(
        createExpressionLanguage(new ZeebeFeelEngineClock(clock), expressionLanguageMetrics));
  }

  public static BpmnValidator createValidator(
      final InstantSource clock,
      final ExpressionProcessor expressionProcessor,
      final BpmnValidatorConfig config,
      final ExpressionLanguageMetrics expressionLanguageMetrics) {
    return new BpmnValidator(
        createExpressionLanguage(new ZeebeFeelEngineClock(clock), expressionLanguageMetrics),
        expressionProcessor,
        config);
  }

  private static ExpressionLanguage createExpressionLanguage(
      final ZeebeFeelEngineClock zeebeFeelEngineClock,
      final ExpressionLanguageMetrics expressionLanguageMetrics) {
    return ExpressionLanguageFactory.createExpressionLanguage(
        zeebeFeelEngineClock, expressionLanguageMetrics);
  }
}
