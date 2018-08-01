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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class JsonConditionTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private final JsonConditionInterpreter interpreter = new JsonConditionInterpreter();

  @Test
  public void shouldEvaluateConditionWithLiteral() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition("$.foo == 'bar'");
    assertThat(condition.isValid()).isTrue();

    boolean result = interpreter.eval(condition.getCondition(), asMsgPack("foo", "bar"));
    assertThat(result).isTrue();

    result = interpreter.eval(condition.getCondition(), asMsgPack("foo", "baz"));
    assertThat(result).isFalse();
  }

  @Test
  public void shouldEvaluateConditionWithJsonPath() {
    final CompiledJsonCondition condition =
        JsonConditionFactory.createCondition("$.foo == $.bar || $.foo > 2 || $.bar <= 2");
    assertThat(condition.isValid()).isTrue();

    boolean result =
        interpreter.eval(condition.getCondition(), asMsgPack(c -> c.put("foo", 2).put("bar", 2)));
    assertThat(result).isTrue();

    result =
        interpreter.eval(condition.getCondition(), asMsgPack(c -> c.put("foo", 2).put("bar", 3)));
    assertThat(result).isFalse();
  }

  @Test
  public void shouldReportParseFailure() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition("$.foo ==");

    assertThat(condition.isValid()).isFalse();
    assertThat(condition.getErrorMessage()).contains("expected literal");
  }

  @Test
  public void shouldFailIfTypeDoesntMatch() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition("$.foo > 3");
    assertThat(condition.isValid()).isTrue();

    thrown.expect(JsonConditionException.class);
    thrown.expectMessage("Cannot compare values of different types: STRING and INTEGER");

    interpreter.eval(condition.getCondition(), asMsgPack("foo", "bar"));
  }

  @Test
  public void shouldFailIfJsonPathDoesntMatch() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition("$.foo > 3");
    assertThat(condition.isValid()).isTrue();

    thrown.expect(JsonConditionException.class);
    thrown.expectMessage("JSON path '$.foo' has no result");

    interpreter.eval(condition.getCondition(), asMsgPack("bar", 4));
  }

  @Test
  public void shouldFailIfTypeIsNil() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition("$.foo > 3");
    assertThat(condition.isValid()).isTrue();

    thrown.expect(JsonConditionException.class);
    thrown.expectMessage("Cannot compare values of different types: NIL and INTEGER");

    interpreter.eval(condition.getCondition(), asMsgPack("foo", null));
  }

  @Test
  public void shouldFailIfTypeIsArray() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition("$.foo == $.bar");
    assertThat(condition.isValid()).isTrue();

    thrown.expect(JsonConditionException.class);
    thrown.expectMessage("Cannot compare value of type: ARRAY");

    final Map<String, Object> map = new HashMap<>();
    map.put("foo", new int[] {1, 2, 3});
    map.put("bar", new int[] {4, 5, 6});

    interpreter.eval(condition.getCondition(), asMsgPack(map));
  }

  @Test
  public void shouldFailIfTypeIsMap() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition("$.foo == $.bar");
    assertThat(condition.isValid()).isTrue();

    thrown.expect(JsonConditionException.class);
    thrown.expectMessage("Cannot compare value of type: MAP");

    final Map<String, Object> map = new HashMap<>();
    map.put("foo", Collections.singletonMap("a", 1));
    map.put("bar", Collections.singletonMap("b", 2));

    interpreter.eval(condition.getCondition(), asMsgPack(map));
  }

  @Test
  public void shouldFailIfTypeIsNotNumber() {
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition("$.foo < $.bar");
    assertThat(condition.isValid()).isTrue();

    thrown.expect(JsonConditionException.class);
    thrown.expectMessage("Cannot compare values. Expected number but found: STRING");

    interpreter.eval(condition.getCondition(), asMsgPack(c -> c.put("foo", "a").put("bar", "b")));
  }
}
