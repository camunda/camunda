/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig.util;

import static io.zeebe.legacy.tomlconfig.util.ByteUnit.BYTES;
import static io.zeebe.legacy.tomlconfig.util.ByteUnit.GIGABYTES;
import static io.zeebe.legacy.tomlconfig.util.ByteUnit.KILOBYTES;
import static io.zeebe.legacy.tomlconfig.util.ByteUnit.MEGABYTES;
import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ByteValueParserTest {

  @Test
  public void shouldParseValidStringValues() {
    assertThat(ByteValueParser.fromString("10").getUnit()).isEqualTo(BYTES);
    assertThat(ByteValueParser.fromString("10").getValue()).isEqualTo(10);

    assertThat(ByteValueParser.fromString("11K").getUnit()).isEqualTo(KILOBYTES);
    assertThat(ByteValueParser.fromString("11").getValue()).isEqualTo(11);

    assertThat(ByteValueParser.fromString("12M").getUnit()).isEqualTo(MEGABYTES);
    assertThat(ByteValueParser.fromString("12").getValue()).isEqualTo(12);

    assertThat(ByteValueParser.fromString("13G").getUnit()).isEqualTo(GIGABYTES);
    assertThat(ByteValueParser.fromString("13").getValue()).isEqualTo(13);
  }

  @Test
  public void shouldParseValidStringValuesCaseInsensitive() {
    assertThat(ByteValueParser.fromString("11k").getUnit()).isEqualTo(KILOBYTES);
    assertThat(ByteValueParser.fromString("12m").getUnit()).isEqualTo(MEGABYTES);
    assertThat(ByteValueParser.fromString("13g").getUnit()).isEqualTo(GIGABYTES);
  }

  @Test
  public void shouldThrowOnInvalidUnit() {
    Assertions.assertThatThrownBy(() -> ByteValueParser.fromString("99f"))
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Illegal byte value");
  }

  @Test
  public void shouldParseOutputOfToStringMethod() {
    final ByteValue value = new ByteValue(71, KILOBYTES);

    assertThat(ByteValueParser.fromString(value.toString())).isEqualTo(value);
  }
}
