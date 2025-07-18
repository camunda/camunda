/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.service.exception.SecondaryStorageUnavailableException;
import org.junit.jupiter.api.Test;

public class SecondaryStorageValidatorTest {

  @Test
  public void shouldThrowExceptionWhenDatabaseTypeIsNone() {
    // Given
    final SecondaryStorageValidator validator = new SecondaryStorageValidator("none");

    // When/Then
    assertThatThrownBy(validator::validateSecondaryStorageEnabled)
        .isInstanceOf(SecondaryStorageUnavailableException.class)
        .hasMessageContaining("This endpoint requires a secondary storage");
  }

  @Test
  public void shouldNotThrowExceptionWhenDatabaseTypeIsElasticsearch() {
    // Given
    final SecondaryStorageValidator validator = new SecondaryStorageValidator("elasticsearch");

    // When/Then - should not throw
    validator.validateSecondaryStorageEnabled();
  }

  @Test
  public void shouldNotThrowExceptionWhenDatabaseTypeIsOpensearch() {
    // Given
    final SecondaryStorageValidator validator = new SecondaryStorageValidator("opensearch");

    // When/Then - should not throw
    validator.validateSecondaryStorageEnabled();
  }
}
