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

import static io.camunda.process.test.api.CamundaAssert.assertThatDecision;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.client.impl.response.MatchedDecisionRuleImpl;
import io.camunda.client.impl.search.response.DecisionInstanceImpl;
import io.camunda.client.protocol.rest.DecisionInstanceResult;
import io.camunda.client.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.client.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.client.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class DecisionInstanceAssertTest {

  private static final String NAME = "name";
  private static final String DECISION_INSTANCE_ID = "instanceId";
  private static final String DECISION_INSTANCE_KEY = "1";
  private static final String PROCESS_DEFINITION_KEY = "2";
  private static final String PROCESS_INSTANCE_KEY = "3";
  private static final String DECISION_DEFINITION_KEY = "4";
  private static final String ELEMENT_INSTANCE_KEY = "5";
  private static final int DECISION_DEFINITION_VERSION = 1;

  private static final String STRING_RESULT = "\"outputValue\"";
  private static final String MAP_RESULT = "{\"a\":\"b\",\"v\":2}";
  private static final String LIST_RESULT = "[{\"a\":1,\"b\":2},{\"c\":3,\"d\":4}]";

  @Mock private CamundaDataSource camundaDataSource;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
  }

  private void mockDecisionInstanceSearch(final DecisionInstance mockedResult) {
    mockDecisionInstanceSearch(Collections.singletonList(mockedResult), mockedResult);
  }

  private void mockDecisionInstanceSearch(
      final List<DecisionInstance> mockedSearchResults,
      final DecisionInstance mockedDecisionInstance) {

    when(camundaDataSource.findDecisionInstances(any())).thenReturn(mockedSearchResults);
    when(camundaDataSource.getDecisionInstance(DECISION_INSTANCE_ID))
        .thenReturn(mockedDecisionInstance);
  }

  private DecisionInstance decisionInstance(
      final Function<DecisionInstanceResult, DecisionInstanceResult> resultBuilderFn) {
    final DecisionInstanceResult basicResult =
        new DecisionInstanceResult()
            .decisionDefinitionName(NAME)
            .decisionInstanceId(DECISION_INSTANCE_ID)
            .decisionInstanceKey(DECISION_INSTANCE_KEY)
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .processInstanceKey(PROCESS_INSTANCE_KEY)
            .decisionDefinitionKey(DECISION_DEFINITION_KEY)
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .decisionDefinitionVersion(DECISION_DEFINITION_VERSION);

    return new DecisionInstanceImpl(resultBuilderFn.apply(basicResult), null);
  }

  private DecisionInstance decisionInstanceWithAnswers(
      final String result, final MatchedDecisionRule... rules) {
    final List<MatchedDecisionRule> rulesList = Arrays.stream(rules).collect(Collectors.toList());

    return new DecisionInstanceImpl(
        null,
        Integer.parseInt(DECISION_INSTANCE_KEY),
        DECISION_INSTANCE_ID,
        DecisionInstanceState.EVALUATED,
        null,
        null,
        Long.parseLong(PROCESS_DEFINITION_KEY),
        Long.parseLong(PROCESS_INSTANCE_KEY),
        Long.parseLong(ELEMENT_INSTANCE_KEY),
        Long.parseLong(DECISION_DEFINITION_KEY),
        "decisionDefinitionId",
        NAME,
        1,
        DecisionDefinitionType.DECISION_TABLE,
        "tenantId",
        Collections.emptyList(),
        rulesList,
        result);
  }

  private MatchedDecisionRule rule(final MatchedDecisionRuleItem ruleItem) {
    return new MatchedDecisionRuleImpl(ruleItem, null);
  }

  private MatchedDecisionRule singleRule() {
    return rule(
        new MatchedDecisionRuleItem()
            .ruleId("ruleId")
            .ruleIndex(1)
            .addEvaluatedOutputsItem(
                new EvaluatedDecisionOutputItem()
                    .outputId("outputId")
                    .outputName("outputName")
                    .outputValue("outputValue")));
  }

  private MatchedDecisionRule[] multiRule() {
    return new MatchedDecisionRule[] {
      rule(
          new MatchedDecisionRuleItem()
              .ruleId("ruleId1")
              .ruleIndex(1)
              .addEvaluatedOutputsItem(
                  new EvaluatedDecisionOutputItem()
                      .outputId("outputId1")
                      .outputName("outputName1")
                      .outputValue("outputValue1"))),
      rule(
          new MatchedDecisionRuleItem()
              .ruleId("ruleId2")
              .ruleIndex(2)
              .addEvaluatedOutputsItem(
                  new EvaluatedDecisionOutputItem()
                      .outputId("outputId2")
                      .outputName("outputName2")
                      .outputValue("outputValue2"))),
      rule(
          new MatchedDecisionRuleItem()
              .ruleId("ruleId3")
              .ruleIndex(3)
              .addEvaluatedOutputsItem(
                  new EvaluatedDecisionOutputItem()
                      .outputId("outputId3")
                      .outputName("outputName3")
                      .outputValue("outputValue3")))
    };
  }

  @Nested
  public class IsEvaluated {

    @Test
    public void isEvaluated() {
      // when
      mockDecisionInstanceSearch(
          decisionInstance(d -> d.state(DecisionInstanceStateEnum.EVALUATED)));

      // then
      assertThatDecision(DecisionSelectors.byName(NAME)).isEvaluated();
    }

    @Test
    @CamundaAssertExpectFailure
    public void evaluationFailure() {
      // when
      mockDecisionInstanceSearch(decisionInstance(d -> d.state(DecisionInstanceStateEnum.FAILED)));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).isEvaluated())
          .hasMessage("Expected DecisionInstance [name] to have been evaluated, but was failed");
    }
  }

  @Nested
  public class HasOutput {

    @Test
    public void hasOutput() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers(STRING_RESULT));

      // then
      assertThatDecision(DecisionSelectors.byName(NAME)).hasOutput("outputValue");
    }

    @Test
    public void waitsForInstanceToHaveCorrectOutput() {
      // when
      final DecisionInstance wrongInstance = decisionInstanceWithAnswers("null");
      final DecisionInstance correctInstance = decisionInstanceWithAnswers(STRING_RESULT);

      when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(Collections.singletonList(wrongInstance))
          .thenReturn(Collections.singletonList(wrongInstance))
          .thenReturn(Collections.singletonList(wrongInstance))
          .thenReturn(Collections.singletonList(correctInstance));
      when(camundaDataSource.getDecisionInstance(DECISION_INSTANCE_ID))
          .thenReturn(wrongInstance)
          .thenReturn(wrongInstance)
          .thenReturn(wrongInstance)
          .thenReturn(correctInstance);

      // then
      assertThatDecision(DecisionSelectors.byName(NAME)).hasOutput("outputValue");
    }

    @Test
    @CamundaAssertExpectFailure
    public void assertionFailureWithStringMatch() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers(STRING_RESULT));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasOutput("foo"))
          .hasMessage(
              "Expected DecisionInstance [name] to have output '\"foo\"', but was '\"outputValue\"'");

      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasOutput(expected))
          .hasMessage(
              "Expected DecisionInstance [name] to have output '{\"a\":\"b\"}', but was '\"outputValue\"'");
    }

    @Test
    @CamundaAssertExpectFailure
    public void assertionFailureWithMapMatch() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers(MAP_RESULT));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasOutput("foo"))
          .hasMessage(
              "Expected DecisionInstance [name] to have output '\"foo\"', but was '{\"a\":\"b\",\"v\":2}'");

      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasOutput(expected))
          .hasMessage(
              "Expected DecisionInstance [name] to have output '{\"a\":\"b\"}', but was '{\"a\":\"b\",\"v\":2}'");
    }

    @Test
    @CamundaAssertExpectFailure
    public void assertionFailureWithListMatch() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers(LIST_RESULT));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasOutput("foo"))
          .hasMessage(
              "Expected DecisionInstance [name] to have output '\"foo\"', but was '[{\"a\":1,\"b\":2},{\"c\":3,\"d\":4}]'");

      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasOutput(expected))
          .hasMessage(
              "Expected DecisionInstance [name] to have output '{\"a\":\"b\"}', but was '[{\"a\":1,\"b\":2},{\"c\":3,\"d\":4}]'");
    }
  }

  @Nested
  public class HasMatchedRulesIndices {
    @Test
    public void hasMatched() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", singleRule()));

      // then
      assertThatDecision(DecisionSelectors.byName(NAME)).hasMatchedRules(1);
    }

    @Test
    public void waitsForDecisionToHaveCorrectMatch() {
      // when
      final DecisionInstance wrongInstance = decisionInstanceWithAnswers("null");
      final DecisionInstance correctInstance =
          decisionInstanceWithAnswers(STRING_RESULT, singleRule());

      when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(Collections.singletonList(wrongInstance))
          .thenReturn(Collections.singletonList(wrongInstance))
          .thenReturn(Collections.singletonList(wrongInstance))
          .thenReturn(Collections.singletonList(correctInstance));
      when(camundaDataSource.getDecisionInstance(DECISION_INSTANCE_ID))
          .thenReturn(wrongInstance)
          .thenReturn(wrongInstance)
          .thenReturn(wrongInstance)
          .thenReturn(correctInstance);

      // then
      assertThatDecision(DecisionSelectors.byName(NAME)).hasMatchedRules(1);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasNotMatched() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", singleRule()));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasMatchedRules(2))
          .hasMessage(
              "Expected DecisionInstance [name] to have matched rules [2], but did not. Matches:\n"
                  + "\t- matched: []\n"
                  + "\t- missing: [2]\n"
                  + "\t- unexpected: [1]");
    }
  }

  @Nested
  public class HasNotMatchedRulesIndices {
    @Test
    public void hasNotMatched() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", singleRule()));

      // then
      assertThatDecision(DecisionSelectors.byName(NAME)).hasNotMatchedRules(2);
    }

    @Test
    public void hasNotMatchedMultiple() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", multiRule()));

      // then
      assertThatDecision(DecisionSelectors.byName(NAME)).hasNotMatchedRules(4, 5, 6);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasMatched() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", singleRule()));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasNotMatchedRules(1))
          .hasMessage(
              "Expected DecisionInstance [name] to not have matched rules [1], but matched [1]");
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasMatchedPartial() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", multiRule()));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatDecision(DecisionSelectors.byName(NAME)).hasNotMatchedRules(4, 1, 5))
          .hasMessage(
              "Expected DecisionInstance [name] to not have matched rules [4, 1, 5], but matched [1]");
    }
  }
}
