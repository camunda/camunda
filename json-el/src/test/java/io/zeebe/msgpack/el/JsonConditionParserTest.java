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
public class JsonConditionParserTest {
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

  @Parameter public String expression;

  @Test
  public void test() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);

    assertThat(condition.isValid())
        .withFailMessage(
            "Expected <valid> condition. Error message: %s", condition.getErrorMessage())
        .isTrue();
  }
}
