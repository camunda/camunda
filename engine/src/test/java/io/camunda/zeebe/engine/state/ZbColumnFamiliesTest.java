/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class ZbColumnFamiliesTest {

  @Test
  void shouldNotReuseEnumValues() {
    assertThat(Arrays.stream(ZbColumnFamilies.values()).map(ZbColumnFamilies::getValue))
        .doesNotHaveDuplicates();
  }

  /** If this test case fails, you can update ZbColumnFamilies to include all known enum values. */
  @Test
  void shouldNotSkipEnumValues() {
    assertThat(
            Arrays.stream(ZbColumnFamilies.values()).mapToInt(ZbColumnFamilies::getValue).toArray())
        .describedAs("The enum values must be sequential")
        .isEqualTo(IntStream.range(0, ZbColumnFamilies.values().length).toArray());
  }

  public static Stream<Arguments> values() {
    return Arrays.stream(ZbColumnFamilies.values()).map(Arguments::of);
  }
}
