/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class CachedBytesEnumTest {
  private final CachedBytesEnum<TestEnum> cachedEnum = CachedBytesEnum.get(TestEnum.class);

  @EnumSource(TestEnum.class)
  @ParameterizedTest
  public void shouldMatchStringRepresentation(final TestEnum testEnum) {
    final var stringRepresentation = BufferUtil.bufferAsString(cachedEnum.byteRepr(testEnum));
    assertThat(stringRepresentation).isEqualTo(testEnum.toString());
  }

  @EnumSource(TestEnum.class)
  @ParameterizedTest
  public void shouldGetEnumValue(final TestEnum testEnum) {
    final var buffer = BufferUtil.wrapString(testEnum.toString());
    assertThat(cachedEnum.getValue(buffer)).isEqualTo(testEnum);
  }

  private enum TestEnum {
    A,
    B,
    C,
    MUCH_LONGER;
  }
}
