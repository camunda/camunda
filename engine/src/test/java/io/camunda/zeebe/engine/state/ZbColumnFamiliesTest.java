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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class ZbColumnFamiliesTest {

  @Test
  void shouldNotReuseEnumValues() {
    assertThat(Arrays.stream(ZbColumnFamilies.values()).map(ZbColumnFamilies::getValue))
        .doesNotHaveDuplicates();
  }

  public static Stream<Arguments> values() {
    return Arrays.stream(ZbColumnFamilies.values()).map(Arguments::of);
  }
}
