/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig;

import io.zeebe.legacy.tomlconfig.LegacyConfigurationSupport.Replacement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class LegacyConfigurationSupportTest {

  @Test
  public void shouldConvertCanonicalToEnvironmentVariableRepresentation() {
    // given
    final List<String> input =
        Arrays.asList("test", "camelCase", "kebab-case", "one.more-complicated.exampleProperty");
    final List<String> expected =
        Arrays.asList("TEST", "CAMELCASE", "KEBABCASE", "ONE_MORECOMPLICATED_EXAMPLEPROPERTY");

    // when
    final List<String> actual = new ArrayList<>();

    input.forEach(canonical -> actual.add(new Replacement(canonical).toEnvironmentVariable()));

    // then
    Assertions.assertThat(actual).isEqualTo(expected);
  }
}
