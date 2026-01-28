/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class ExpressionProcessors {

  private ExpressionProcessors() {}

  public static void addProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final ExpressionBehavior expressionBehavior,
      final ExpressionLanguage expressionLanguage,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final var validator = new ExpressionValidator(expressionLanguage);
    typedRecordProcessors.onCommand(
        ValueType.EXPRESSION,
        ExpressionIntent.EVALUATE,
        new ExpressionEvaluateProcessor(
            keyGenerator, writers, expressionBehavior, validator, authCheckBehavior));
  }
}
