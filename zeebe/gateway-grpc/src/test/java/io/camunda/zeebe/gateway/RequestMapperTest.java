/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;

public class RequestMapperTest {

  // missing closing quote in second variable
  private static final String INVALID_VARIABLES =
      "{ \"test\": \"value\", \"error\": \"errorrvalue }";

  // missing closing quote in "denied"
  private static final String INVALID_RESULT =
      """
        {
          "result": {
            "denied: true
          }
        }
      """;

  // BigInteger larger than 2^64-1
  private static final String BIG_INTEGER =
      "{\"mybigintistoolong\": 123456789012345678901234567890}";

  @Test
  public void shouldThrowHelpfulExceptionIfJsonIsInvalid() {
    // when + then
    assertThatThrownBy(() -> RequestMapper.ensureJsonSet(INVALID_VARIABLES))
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid JSON", INVALID_VARIABLES)
        .cause()
        .isInstanceOf(JsonParseException.class);
  }

  @Test
  public void shouldThrowHelpfulExceptionIfJsonIsInvalidForResult() {
    // when + then
    assertThatThrownBy(() -> RequestMapper.ensureJsonSet(INVALID_RESULT))
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid JSON", INVALID_RESULT)
        .cause()
        .isInstanceOf(JsonParseException.class);
  }

  @Test
  public void shouldThrowHelpfulExceptionIfJsonHasBigInteger() {
    // when + then
    assertThatThrownBy(() -> RequestMapper.ensureJsonSet(BIG_INTEGER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MessagePack cannot serialize BigInteger larger than 2^64-1");
  }
}
