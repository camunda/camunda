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
package io.camunda.process.test.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.process.test.impl.assertions.DecisionOutputAssertj;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DecisionOutputAssertTest {

  private static final String FAILURE_MESSAGE_PREFIX = "Expected DecisionInstance [name]";

  private final DecisionOutputAssertj decisionOutputAssert =
      new DecisionOutputAssertj(
          new CamundaAssertJsonMapper(new CamundaObjectMapper(new ObjectMapper())),
          FAILURE_MESSAGE_PREFIX);

  private static Stream<Arguments> outputScenarios() {
    return Stream.of(
        Arguments.of("\"output\"", "output"),
        Arguments.of("5", 5),
        Arguments.of("true", true),
        Arguments.of("{\"a\":\"b\",\"v\":2}", outputMap()),
        Arguments.of("[{\"a\":1,\"b\":2},{\"c\":3,\"d\":4}]", outputList()),
        Arguments.of("null", null),
        Arguments.of("[1, 2, 3]", Arrays.asList(1, 2, 3)));
  }

  private static Map<String, Object> outputMap() {
    final Map<String, Object> expected = new HashMap<>();
    expected.put("a", "b");
    expected.put("v", 2);

    return expected;
  }

  private static List<Map<String, Object>> outputList() {
    final Map<String, Object> firstMatch = new HashMap<>();
    firstMatch.put("a", 1);
    firstMatch.put("b", 2);
    final Map<String, Object> secondMatch = new HashMap<>();
    secondMatch.put("c", 3);
    secondMatch.put("d", 4);

    return Arrays.asList(firstMatch, secondMatch);
  }

  @ParameterizedTest
  @MethodSource("io.camunda.process.test.api.DecisionOutputAssertTest#outputScenarios")
  void hasOutput(final String actual, final Object expected) {
    decisionOutputAssert.hasOutput(actual, expected);
  }
}
