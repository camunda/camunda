/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JsonPathQueryValidationTest {
  @Parameter(0)
  public String jsonPath;

  @Parameter(1)
  public int expectedInvalidPosition;

  @Parameter(2)
  public String expectedErrorMessage;

  @Parameters
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"$", 0, "Unexpected json-path token ROOT_OBJECT"},
          {"$.foo", 0, "Unexpected json-path token ROOT_OBJECT"},
          {"foo.$", 4, "Unexpected json-path token ROOT_OBJECT"},
          {"foo.*", 4, "Unexpected json-path token WILDCARD"},
          {"foo[0]", 3, "Unexpected json-path token SUBSCRIPT_OPERATOR_BEGIN"}
        });
  }

  @Test
  public void testCompileInvalidQuery() {
    // given
    final JsonPathQueryCompiler compiler = new JsonPathQueryCompiler();

    // when
    final JsonPathQuery jsonPathQuery = compiler.compile(jsonPath);

    // then
    assertThat(jsonPathQuery.isValid()).isFalse(); // as recursion is not yet supported
    assertThat(jsonPathQuery.getInvalidPosition()).isEqualTo(expectedInvalidPosition);
    assertThat(jsonPathQuery.getErrorReason()).isEqualTo(expectedErrorMessage);
  }
}
