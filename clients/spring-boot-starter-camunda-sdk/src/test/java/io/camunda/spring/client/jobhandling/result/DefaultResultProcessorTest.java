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

class DefaultResultProcessorTest {

  private final DefaultResultProcessor defaultResultProcessor = new DefaultResultProcessor();

  @Test
  public void testProcessMethodShouldReturnResult() {
    // Given
    final String inputValue = "input";
    // When
    final Object resultValue = defaultResultProcessor.process(inputValue);
    // Then
    Assertions.assertEquals(inputValue, resultValue);
  }
}
