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

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JsonConditionInterpreterTest {

  private final JsonConditionInterpreter interpreter = new JsonConditionInterpreter();

  @Parameters(name = "{index}: expression = {0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"foo == 'bar'", asMsgPack("foo", "bar"), true},
          {"foo == 'bar'", asMsgPack("foo", "baz"), false},
          {"foo == true", asMsgPack("foo", true), true},
          {"foo == true", asMsgPack("foo", false), false},
          {"foo == 3", asMsgPack("foo", 3), true},
          {"foo == 3", asMsgPack("foo", 4), false},
          {"foo == 2.5", asMsgPack("foo", 2.5), true},
          {"foo == 2.5", asMsgPack("foo", 2.6), false},
          {"foo == 2", asMsgPack("foo", 2.0), true},
          {"foo == 2.0", asMsgPack("foo", 2), true},
          {"foo == null", asMsgPack("foo", null), true},
          {"foo == null", asMsgPack("foo", "bar"), false},
          {"foo == bar", asMsgPack(c -> c.put("foo", "a").put("bar", "a")), true},
          {"foo == bar", asMsgPack(c -> c.put("foo", "a").put("bar", "b")), false},
          {"foo != 'bar'", asMsgPack("foo", "baz"), true},
          {"foo != 'bar'", asMsgPack("foo", "bar"), false},
          {"foo != null", asMsgPack("foo", "bar"), true},
          {"foo != null", asMsgPack("foo", null), false},
          {"foo < 5", asMsgPack("foo", 4), true},
          {"foo < 5", asMsgPack("foo", 5), false},
          {"foo <= 5", asMsgPack("foo", 5), true},
          {"foo <= 5", asMsgPack("foo", 6), false},
          {"foo <= 5.0", asMsgPack("foo", 4.8), true},
          {"foo <= 5.0", asMsgPack("foo", 5), true},
          {"foo <= 5.0", asMsgPack("foo", 5.1), false},
          {"foo > 5", asMsgPack("foo", 6), true},
          {"foo > 5", asMsgPack("foo", 5), false},
          {"foo >= 5", asMsgPack("foo", 5), true},
          {"foo >= 5", asMsgPack("foo", 4), false},
          {"foo < bar", asMsgPack(c -> c.put("foo", 1).put("bar", 2)), true},
          {"foo < bar", asMsgPack(c -> c.put("foo", 2).put("bar", 2)), false},
          {"foo == 1 || foo == 2", asMsgPack("foo", 1), true},
          {"foo == 1 || foo == 2", asMsgPack("foo", 2), true},
          {"foo == 1 || foo == 2", asMsgPack("foo", 3), false},
          {"foo == 1 || foo == 2 || foo == 3", asMsgPack("foo", 3), true},
          {"foo == 1 || foo == 2 || foo == 3", asMsgPack("foo", 4), false},
          {"foo > 2 && foo > 3", asMsgPack("foo", 4), true},
          {"foo > 2 && foo > 3", asMsgPack("foo", 3), false},
          {"foo > 2 && foo > 3", asMsgPack("foo", 2), false},
          {"foo > 2 && foo > 3 && foo > 4", asMsgPack("foo", 5), true},
          {"foo > 2 && foo > 3 && foo > 4", asMsgPack("foo", 4), false},
          {"foo == 1 || foo > 2 && foo > 3", asMsgPack("foo", 4), true},
          {"foo == 1 || foo > 2 && foo > 3", asMsgPack("foo", 1), true},
          {"foo == 1 || foo > 2 && foo > 3", asMsgPack("foo", 2), false},
          {"foo == 1 || foo > 2 && foo > 3", asMsgPack("foo", 3), false},
          {"(foo == 1 || foo > 2) && foo > 3", asMsgPack("foo", 4), true},
          {"(foo == 1 || foo > 2) && foo > 3", asMsgPack("foo", 1), false},
          {"(foo == 1 || foo > 2) && foo > 3", asMsgPack("foo", 2), false},
          {"(foo == 1 || foo > 2) && foo > 3", asMsgPack("foo", 3), false},
          {"foo < 5", asMsgPack("foo", Double.NaN), false},
          {"foo > 5", asMsgPack("foo", Double.NaN), false},
          {"foo < 5", asMsgPack("foo", Double.POSITIVE_INFINITY), false},
          {"foo > 5", asMsgPack("foo", Double.POSITIVE_INFINITY), true},
          {"foo < 5", asMsgPack("foo", Double.NEGATIVE_INFINITY), true},
          {"foo > 5", asMsgPack("foo", Double.NEGATIVE_INFINITY), false},
        });
  }

  @Parameter(0)
  public String expression;

  @Parameter(1)
  public DirectBuffer json;

  @Parameter(2)
  public boolean isFulfilled;

  @Test
  public void test() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);
    assertThat(condition.isValid())
        .withFailMessage("Invalid condition: %s", condition.getErrorMessage())
        .isTrue();

    final boolean result = interpreter.eval(condition, json);
    assertThat(result).describedAs("is fulfilled").isEqualTo(isFulfilled);
  }
}
