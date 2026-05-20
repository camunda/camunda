/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcessViewAgentAvgTokensPerCallInterpreterESTest {

  @Test
  void shouldReturnNullIfDenominatorIsZero() {
    // given
    final Double numerator = 1250.0;
    final Double denominator = 0.0;

    // when
    final Double ratio =
        ProcessViewAgentAvgTokensPerCallInterpreterES.calculateRatio(numerator, denominator);

    // then
    assertThat(ratio).isNull();
  }

  @Test
  void shouldReturnRatioForPositiveDenominator() {
    // given
    final Double numerator = 1381.0;
    final Double denominator = 2.0;

    // when
    final Double ratio =
        ProcessViewAgentAvgTokensPerCallInterpreterES.calculateRatio(numerator, denominator);

    // then
    assertThat(ratio).isEqualTo(690.5);
  }
}
