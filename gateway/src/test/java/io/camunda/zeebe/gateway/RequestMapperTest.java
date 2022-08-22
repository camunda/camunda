/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;

public class RequestMapperTest {

  // missing closing quote in second variable
  private static final String INVALID_VARIABLES =
      "{ \"test\": \"value\", \"error\": \"errorrvalue }";

  @Test
  public void shouldThrowHelpfulExceptionIfJsonIsInvalid() {
    // when + then
    assertThatThrownBy(() -> RequestMapper.ensureJsonSet(INVALID_VARIABLES))
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid JSON", INVALID_VARIABLES)
        .cause()
        .isInstanceOf(JsonParseException.class);
  }
}
