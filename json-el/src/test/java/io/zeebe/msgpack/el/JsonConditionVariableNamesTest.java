/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.el;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JsonConditionVariableNamesTest {

  @Parameter public String expression;

  @Parameter(1)
  public DirectBuffer[] expectedVariableNames;

  private static DirectBuffer[] fromStrings(String... args) {
    return Arrays.stream(args)
        .map(str -> BufferUtil.wrapString(str))
        .collect(Collectors.toList())
        .toArray(new DirectBuffer[args.length]);
  }

  @Parameters(name = "{index}: expression = {0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"foo == bar || foo > 2 || bar <= 2", fromStrings("foo", "bar")},
          {"foo == true", fromStrings("foo")},
          {"foo == 21", fromStrings("foo")},
          {"foo == 2.5", fromStrings("foo")},
          {"foo == bar", fromStrings("foo", "bar")},
          {"foo.bar == true", fromStrings("foo")},
          {"foo != 'bar'", fromStrings("foo")},
          {"2 < 4", fromStrings()},
          {"foo.bar > 2 || bar.foo < 4 || foobar == 21", fromStrings("foo", "bar", "foobar")},
          {"foo > 2 && (foo < 4 || bar == 6)", fromStrings("foo", "bar")}
        });
  }

  @Test
  public void shouldReturnOnlyUniqueVariableNames() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);
    assertThat(condition.isValid()).isTrue();

    final Set<DirectBuffer> actualVariables = condition.getVariableNames();
    assertThat(actualVariables).containsExactlyInAnyOrder(expectedVariableNames);
  }
}
