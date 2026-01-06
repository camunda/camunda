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
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.GlobalListenersConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.deployment.transform.BpmnValidator;
import java.time.InstantSource;
import java.util.Objects;

public final class BpmnFactory {

  public BpmnFactory() {
    /* utility class */
  }

  public static BpmnTransformer createTransformer(
      final InstantSource clock, final EngineConfiguration configuration) {
    final GlobalListenersConfiguration listenerConfig;
    if (configuration != null) {
      listenerConfig =
          Objects.requireNonNullElse(
              configuration.getListeners(), GlobalListenersConfiguration.empty());
    } else {
      listenerConfig = GlobalListenersConfiguration.empty();
    }
    return new BpmnTransformer(
        createExpressionLanguage(new ZeebeFeelEngineClock(clock)), listenerConfig);
  }

  public static BpmnValidator createValidator(
      final InstantSource clock,
      final ExpressionProcessor expressionProcessor,
      final int validatorResultsOutputMaxSize) {
    return new BpmnValidator(
        createExpressionLanguage(new ZeebeFeelEngineClock(clock)),
        expressionProcessor,
        validatorResultsOutputMaxSize);
  }

  private static ExpressionLanguage createExpressionLanguage(
      final ZeebeFeelEngineClock zeebeFeelEngineClock) {
    return ExpressionLanguageFactory.createExpressionLanguage(zeebeFeelEngineClock);
  }
}
