/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.el;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JsonConditionParserTest {
  @Parameter public String expression;

  @Parameters(name = "{index}: expression = {0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"foo == 'bar'"},
          {"foo == \"bar\""},
          {"foo == true"},
          {"foo == 21"},
          {"foo == 2.5"},
          {"foo == bar"},
          {"foo.bar == true"},
          {"'foo' == 'bar'"},
          {"foo != 'bar'"},
          {"foo < 100"},
          {"foo <= -100"},
          {"foo > 2.5"},
          {"foo >= 2.5"},
          {"foo >= .5"},
          {"foo >= -.5"},
          {"foo >= bar"},
          {"2 < 4"},
          {"foo > 2 && foo < 4"},
          {"foo > 2 && foo < 4 && bar > 12"},
          {"foo > 2 || bar < 4"},
          {"foo > 2 || bar < 4 || foobar == 21"},
          {"foo > 2 && foo < 4 || bar == 6"},
          {"(foo == 2)"},
          {"foo > 2 && (foo < 4 || bar == 6)"}
        });
  }

  @Test
  public void test() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);

    assertThat(condition.isValid())
        .withFailMessage(
            "Expected <valid> condition. Error message: %s", condition.getErrorMessage())
        .isTrue();
  }
}
