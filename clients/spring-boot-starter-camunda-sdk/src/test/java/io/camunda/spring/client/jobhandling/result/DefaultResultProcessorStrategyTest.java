/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.jobhandling.result;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultResultProcessorStrategyTest {

  private final DefaultResultProcessorStrategy resultProcessorStrategy =
      new DefaultResultProcessorStrategy();

  @Test
  void createProcessorShouldReturnDefaultProcessor() {
    // Given
    final String inputValue = "input";
    // When
    final ResultProcessor resultProcessor =
        resultProcessorStrategy.createProcessor(inputValue.getClass());
    // Then
    Assertions.assertTrue(resultProcessor instanceof DefaultResultProcessor);
  }
}
