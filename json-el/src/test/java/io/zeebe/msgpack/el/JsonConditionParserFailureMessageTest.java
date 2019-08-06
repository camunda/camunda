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
public class JsonConditionParserFailureMessageTest {
  @Parameter(0)
  public String expression;

  @Parameter(1)
  public String errorMessage;

  @Parameters(name = "{index}: expression = {0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"", "expression is empty"},
          {"?", "expected comparison, disjunction or conjunction."},
          {"foo", "expected comparison operator ('==', '!=', '<', '<=', '>', '>=')"},
          {"foo ==", "expected literal (JSON path, string, number, boolean, null)"},
          {"foo < 'bar'", "expected number or variable"},
          {"foo < true", "expected number or variable"},
          {"foo == { 'a': 2 }", "expected literal (JSON path, string, number, boolean, null)"},
          {"foo == [1, 2, 3]", "expected literal (JSON path, string, number, boolean, null)"},
          {"foo + 3", "expected comparison operator ('==', '!=', '<', '<=', '>', '>=')"},
          {"foo or bar", "expected comparison operator ('==', '!=', '<', '<=', '>', '>=')"},
          {"foo < 3 &&", "end of input expected"},
          {"foo < 3 ||", "end of input expected"},
          {"(foo < 3", "')' expected but end of source found"},
          {"$.foo < 3", "Unexpected json-path"},
        });
  }

  @Test
  public void test() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);

    assertThat(condition.isValid()).describedAs("is valid").isFalse();

    assertThat(condition.getErrorMessage()).contains(errorMessage);
  }
}
