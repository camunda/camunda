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

  @Parameter public String expression;

  @Parameter(1)
  public DirectBuffer[] expectedVariableNames;

  @Test
  public void shouldReturnOnlyUniqueVariableNames() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);
    assertThat(condition.isValid()).isTrue();

    final Set<DirectBuffer> actualVariables = condition.getVariableNames();
    assertThat(actualVariables).containsExactlyInAnyOrder(expectedVariableNames);
  }
}
