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

  @Test
  public void shouldThrowHelpfulExceptionIfJsonIsInvalid() {
    // given
    final var invalidJson = "{ \"test\": \"value\", \"error\": \"errorrvalue }";
    // closing quote missing in second value

    // when + then
    assertThatThrownBy(() -> RequestMapper.ensureJsonSet(invalidJson))
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid JSON", invalidJson)
        .getCause()
        .isInstanceOf(JsonParseException.class);
  }
}
