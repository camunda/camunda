/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DecisionEvaluationInstanceKeyGeneratorTest {

  private DecisionEvaluationInstanceKeyGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new DecisionEvaluationInstanceKeyGenerator(42L);
  }

  @Test
  void shouldGenerateNextKey() {
    final String result = generator.next();
    assertThat("42-1").isEqualTo(result);
  }

  @Test
  void shouldIncrementOnEachCall() {
    assertThat("42-1").isEqualTo(generator.next());
    assertThat("42-2").isEqualTo(generator.next());
    assertThat("42-3").isEqualTo(generator.next());
  }

  @Test
  void testMultipleGeneratorsIndependentCounters() {
    final DecisionEvaluationInstanceKeyGenerator gen1 =
        new DecisionEvaluationInstanceKeyGenerator(1L);
    final DecisionEvaluationInstanceKeyGenerator gen2 =
        new DecisionEvaluationInstanceKeyGenerator(2L);

    assertThat("1-1").isEqualTo(gen1.next());
    assertThat("2-1").isEqualTo(gen2.next());
    assertThat("1-2").isEqualTo(gen1.next());
    assertThat("2-2").isEqualTo(gen2.next());
  }
}
