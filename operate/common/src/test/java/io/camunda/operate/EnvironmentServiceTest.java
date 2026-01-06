/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.env.Environment;

public class EnvironmentServiceTest {

  private EnvironmentService environmentService;
  private Environment mockEnvironment;

  @BeforeEach
  public void setup() {
    mockEnvironment = mock(Environment.class);
    environmentService = new EnvironmentService(mockEnvironment);
  }

  @ParameterizedTest
  @CsvSource({
    "elasticsearch, document-store",
    "opensearch, document-store",
    "rdbms, rdbms",
    "none, none",
    "invalid, unknown"
  })
  void testGetDatabaseType(final String environmentValue, final String expectedOutput) {
    // given
    when(mockEnvironment.getProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE))
        .thenReturn(environmentValue);

    // when
    final String result = environmentService.getDatabaseType();

    // then
    assertThat(result).isEqualTo(expectedOutput);
  }
}
