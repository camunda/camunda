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
          {"foo < 3 &&", "expected comparison"},
          {"foo < 3 ||", "expected comparison"},
          {"(foo < 3", "`)' expected but end of source found"},
          {"$.foo < 3", "Unexpected json-path"},
        });
  }

  @Parameter(0)
  public String expression;

  @Parameter(1)
  public String errorMessage;

  @Test
  public void test() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);

    assertThat(condition.isValid()).describedAs("is valid").isFalse();

    assertThat(condition.getErrorMessage()).contains(errorMessage);
  }
}
