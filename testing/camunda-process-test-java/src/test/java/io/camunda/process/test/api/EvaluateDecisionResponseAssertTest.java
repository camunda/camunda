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

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.impl.response.EvaluateDecisionResponseImpl;
import io.camunda.client.protocol.rest.EvaluateDecisionResult;
import io.camunda.client.protocol.rest.EvaluatedDecisionResult;
import io.camunda.client.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EvaluateDecisionResponseAssertTest {

  private static final String NAME = "name";
  private static final String DECISION_DEFINITION_ID = "decisionDefinitionId";
  private static final String DECISION_INSTANCE_KEY = "1";
  private static final String DECISION_DEFINITION_KEY = "4";
  private static final int DECISION_DEFINITION_VERSION = 1;
  private static final String DECISION_REQUIREMENTS_KEY = "5";

  private static final String STRING_RESULT = "\"outputValue\"";
  private static final String MAP_RESULT = "{\"a\":\"b\",\"v\":2}";
  private static final String LIST_RESULT = "[{\"a\":1,\"b\":2},{\"c\":3,\"d\":4}]";

  @Mock private CamundaDataSource camundaDataSource;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
    CamundaAssert.setAssertionInterval(Duration.ZERO);
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(1));
  }

  @AfterEach
  void resetAssertions() {
    CamundaAssert.setAssertionInterval(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
  }

  private EvaluateDecisionResponse evaluateDecisionResponse(
      final String result, final Supplier<List<EvaluatedDecisionResult>> decisionSupplier) {

    return new EvaluateDecisionResponseImpl(
        new EvaluateDecisionResult()
            .decisionDefinitionKey(DECISION_DEFINITION_KEY)
            .output(result)
            .decisionInstanceKey(DECISION_INSTANCE_KEY)
            .decisionDefinitionId(NAME)
            .decisionDefinitionVersion(DECISION_DEFINITION_VERSION)
            .decisionRequirementsKey(DECISION_REQUIREMENTS_KEY)
            .failureMessage(null)
            .evaluatedDecisions(decisionSupplier.get()),
        null);
  }

  private EvaluatedDecisionResult decisionResult(
      final String result, final MatchedDecisionRuleItem... rules) {
    final List<MatchedDecisionRuleItem> rulesList =
        Arrays.stream(rules).collect(Collectors.toList());

    return new EvaluatedDecisionResult()
        .decisionDefinitionKey(DECISION_DEFINITION_KEY)
        .decisionDefinitionVersion(DECISION_DEFINITION_VERSION)
        .matchedRules(rulesList)
        .output(result);
  }

  @Nested
  public class IsEvaluated {

    @Test
    public void isEvaluated() {
      // then
      assertThat(evaluateDecisionResponse(STRING_RESULT, Collections::emptyList)).isEvaluated();
    }

    @Test
    public void evaluationFailure() {
      // given
      final EvaluateDecisionResponseImpl evaluateDecisionResponse =
          new EvaluateDecisionResponseImpl(
              new EvaluateDecisionResult()
                  .decisionDefinitionId(DECISION_DEFINITION_ID)
                  .decisionDefinitionKey(DECISION_DEFINITION_KEY)
                  .output(null)
                  .decisionInstanceKey(DECISION_INSTANCE_KEY)
                  .decisionDefinitionId(NAME)
                  .decisionDefinitionVersion(DECISION_DEFINITION_VERSION)
                  .decisionRequirementsKey(DECISION_REQUIREMENTS_KEY)
                  .failureMessage("Something went wrong.")
                  .evaluatedDecisions(Collections.emptyList()),
              null);

      // then
      Assertions.assertThatThrownBy(() -> assertThat(evaluateDecisionResponse).isEvaluated())
          .hasMessage("EvaluateDecisionResponse [name] failed with message: Something went wrong.");
    }
  }

  @Nested
  public class HasOutput {

    @Test
    public void hasOutputWithStringMatch() {
      // then
      assertThat(
              evaluateDecisionResponse(
                  STRING_RESULT, () -> Collections.singletonList(decisionResult(STRING_RESULT))))
          .hasOutput("outputValue");
    }

    @Test
    public void hasOutputWithSingleMapMatch() {
      // then
      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      expected.put("v", 2);

      assertThat(
              evaluateDecisionResponse(
                  MAP_RESULT, () -> Collections.singletonList(decisionResult(MAP_RESULT))))
          .hasOutput(expected);
    }

    @Test
    public void hasOutputWithListMatch() {
      // then
      final Map<String, Object> firstMatch = new HashMap<>();
      firstMatch.put("a", 1);
      firstMatch.put("b", 2);
      final Map<String, Object> secondMatch = new HashMap<>();
      secondMatch.put("c", 3);
      secondMatch.put("d", 4);

      // All of these should match
      assertThat(
              evaluateDecisionResponse(
                  LIST_RESULT, () -> Collections.singletonList(decisionResult(LIST_RESULT))))
          .hasOutput(Arrays.asList(firstMatch, secondMatch));
    }

    @Test
    public void assertionFailureWithStringMatch() {
      // when
      final EvaluateDecisionResponse decisionResponse =
          evaluateDecisionResponse(
              STRING_RESULT, () -> Collections.singletonList(decisionResult(STRING_RESULT)));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(decisionResponse).hasOutput("foo"))
          .hasMessage(
              "Expected EvaluateDecisionResponse [name] to have output '\"foo\"', but was '\"outputValue\"'");

      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      Assertions.assertThatThrownBy(() -> assertThat(decisionResponse).hasOutput(expected))
          .hasMessage(
              "Expected EvaluateDecisionResponse [name] to have output '{\"a\":\"b\"}', but was '\"outputValue\"'");
    }

    @Test
    public void assertionFailureWithMapMatch() {
      // when
      final EvaluateDecisionResponse decisionResponse =
          evaluateDecisionResponse(
              MAP_RESULT, () -> Collections.singletonList(decisionResult(MAP_RESULT)));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(decisionResponse).hasOutput("foo"))
          .hasMessage(
              "Expected EvaluateDecisionResponse [name] to have output '\"foo\"', but was '{\"a\":\"b\",\"v\":2}'");

      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      Assertions.assertThatThrownBy(() -> assertThat(decisionResponse).hasOutput(expected))
          .hasMessage(
              "Expected EvaluateDecisionResponse [name] to have output '{\"a\":\"b\"}', but was '{\"a\":\"b\",\"v\":2}'");
    }

    @Test
    public void assertionFailureWithListMatch() {
      // when
      final EvaluateDecisionResponse decisionResponse =
          evaluateDecisionResponse(
              LIST_RESULT, () -> Collections.singletonList(decisionResult(LIST_RESULT)));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(decisionResponse).hasOutput("foo"))
          .hasMessage(
              "Expected EvaluateDecisionResponse [name] to have output '\"foo\"', but was '[{\"a\":1,\"b\":2},{\"c\":3,\"d\":4}]'");

      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      Assertions.assertThatThrownBy(() -> assertThat(decisionResponse).hasOutput(expected))
          .hasMessage(
              "Expected EvaluateDecisionResponse [name] to have output '{\"a\":\"b\"}', but was '[{\"a\":1,\"b\":2},{\"c\":3,\"d\":4}]'");
    }
  }
}
