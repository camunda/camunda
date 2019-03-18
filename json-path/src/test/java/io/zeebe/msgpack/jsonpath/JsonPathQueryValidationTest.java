/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  @Parameter(0)
  public String jsonPath;

  @Parameter(1)
  public int expectedInvalidPosition;

  @Parameter(2)
  public String expectedErrorMessage;

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
