/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.property.LongProperty;
import org.junit.jupiter.api.Test;

final class DecodingTest {

  @Test
  void shouldReturnTheValueWhenItIsSet() {
    // given
    final String value = "set";

    // when
    final String result = Decoding.required(value, "key");

    // then
    assertThat(result).isSameAs(value);
  }

  @Test
  void shouldFailLikeAnUnsetLegacyPropertyWhenTheValueIsNull() {
    // given
    final var legacy = new LongProperty("key");
    final Throwable legacyFailure =
        org.assertj.core.api.Assertions.catchThrowable(legacy::getValue);

    // when
    final Throwable failure =
        org.assertj.core.api.Assertions.catchThrowable(() -> Decoding.required(null, "key"));

    // then
    assertThat(failure)
        .isExactlyInstanceOf(MsgpackPropertyException.class)
        .isExactlyInstanceOf(legacyFailure.getClass())
        .hasMessage(legacyFailure.getMessage());
    assertThat(((MsgpackPropertyException) failure).getProperty()).isEqualTo("key");
    assertThatThrownBy(() -> Decoding.required(null, "key"))
        .hasMessage(
            "Property 'key' is invalid: Expected a value or default value to be specified, but has nothing");
  }
}
