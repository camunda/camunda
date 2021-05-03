/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ByteValueTest {

  @Parameterized.Parameter(0)
  public long input;

  @Parameterized.Parameter(1)
  public String expected;

  @Parameterized.Parameters(name = "Byte value {0} -> {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {0, "0 B"},
      {1024, "1.0 KB"},
      {1500, "1.5 KB"},
      {1048576, "1.0 MB"},
      {3670016, "3.5 MB"},
      {36700160, "35.0 MB"}
    };
  }

  @Test
  public void shouldPrettyPrintByteValue() {

    // when
    final String actual = ByteValue.prettyPrint(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }
}
