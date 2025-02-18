/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.Test;

class CombinedEvaluationContextTest {

  @Test
  void providesNullForEmptyContext() {
    final var context = CombinedEvaluationContext.withContexts();
    assertNull(context.getVariable("a"));
  }

  @Test
  void providesValueFromGivenContext() {
    final var context =
        CombinedEvaluationContext.withContexts(name -> BufferUtil.wrapString("Hello"));

    final var resultValue = context.getVariable("any");

    assertEquals(BufferUtil.wrapString("Hello"), resultValue);
  }

  @Test
  void providesValueFromLastGivenContext() {
    final var context =
        CombinedEvaluationContext.withContexts(
            name -> BufferUtil.wrapString("Hello"), name -> BufferUtil.wrapString("World"));

    final var resultValue = context.getVariable("any");

    assertEquals(BufferUtil.wrapString("Hello"), resultValue);
  }
}
