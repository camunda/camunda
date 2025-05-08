/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.response.MatchedDecisionRuleImpl;
import io.camunda.client.impl.search.response.DecisionInstanceImpl;
import io.camunda.client.protocol.rest.DecisionInstanceResult;
import io.camunda.client.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.client.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.client.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
public class DecisionInstanceAssertTest {

  private static final String NAME = "name";
  private static final String DECISION_INSTANCE_ID = "instanceId";
  private static final String DECISION_INSTANCE_KEY = "1";
  private static final String PROCESS_DEFINITION_KEY = "2";
  private static final String PROCESS_INSTANCE_KEY = "3";
  private static final String DECISION_DEFINITION_KEY = "4";
  private static final int DECISION_DEFINITION_VERSION = 1;

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

  @Nested
  public class IsEvaluated {

    @Test
    public void isEvaluated() {
      // when
      mockDecisionInstanceSearch(
          decisionInstance(d -> d.state(DecisionInstanceStateEnum.EVALUATED)));

      // then
      assertThat(DecisionSelectors.byName(NAME)).isEvaluated();
    }

    @Test
    public void evaluationFailure() {
      // when
      mockDecisionInstanceSearch(decisionInstance(d -> d.state(DecisionInstanceStateEnum.FAILED)));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(DecisionSelectors.byName(NAME)).isEvaluated())
          .hasMessage("Expected [name] to have been evaluated, but was failed");
    }
  }

  @Nested
  public class HasName {

    @Test
    public void hasName() {
      // when
      mockDecisionInstanceSearch(decisionInstance());

      // then
      assertThat(DecisionSelectors.byName(NAME)).hasName(NAME);
    }

    @Test
    public void nameMismatch() {
      // when
      mockDecisionInstanceSearch(decisionInstance());

      // then
      Assertions.assertThatThrownBy(() -> assertThat(DecisionSelectors.byName(NAME)).hasName("b"))
          .hasMessage("Expected [name] to have name 'b', but was 'name'");
    }
  }

  @Nested
  public class HasId {

    @Test
    public void hasId() {
      // when
      mockDecisionInstanceSearch(
          decisionInstance(d -> d.decisionDefinitionId("decisionDefinitionId")));

      // then
      assertThat(DecisionSelectors.byName(NAME)).hasId("decisionDefinitionId");
    }

    @Test
    public void idMismatch() {
      // when
      mockDecisionInstanceSearch(
          decisionInstance(d -> d.decisionDefinitionId("decisionDefinitionId")));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(DecisionSelectors.byName(NAME)).hasId("foo"))
          .hasMessage("Expected [name] to have id 'foo', but was 'decisionDefinitionId'");
    }
  }

  @Nested
  public class HasVersion {

    @Test
    public void hasVersion() {
      // when
      mockDecisionInstanceSearch(decisionInstance(d -> d.decisionDefinitionVersion(1)));

      // then
      assertThat(DecisionSelectors.byName(NAME)).hasVersion(1);
    }

    @Test
    public void versionMismatch() {
      // when
      mockDecisionInstanceSearch(decisionInstance(d -> d.decisionDefinitionVersion(1)));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(DecisionSelectors.byName(NAME)).hasVersion(2))
          .hasMessage("Expected [name] to have version 2, but was 1");
    }
  }

  @Nested
  public class HasOutput {

    @Test
    public void hasOutputValueWithSingleMatch() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue"));

      // then
      assertThat(DecisionSelectors.byName(NAME)).containsOutput("outputValue");
    }

    @Test
    public void containsOutputPartiallyMatches() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("{\"a\":\"b\",\"v\":2}"));

      // then
      assertThat(DecisionSelectors.byName(NAME)).containsOutput("b").containsOutput("2");
    }

    @Test
    public void hasOutputPartiallyMatches() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("{\"a\":\"b\",\"v\":2}"));
      when(camundaDataSource.getJsonMapper()).thenReturn(new CamundaObjectMapper());

      // then
      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      assertThat(DecisionSelectors.byName(NAME)).hasOutput(expected);
    }

    @Test
    public void hasOutputMatchesAVariableMap() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("{\"a\":\"b\",\"v\":2}"));
      when(camundaDataSource.getJsonMapper()).thenReturn(new CamundaObjectMapper());

      // then
      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      expected.put("v", 2);
      assertThat(DecisionSelectors.byName(NAME)).hasOutput(expected);
    }

    @Test
    public void noMatchingOutputValue() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", singleRule()));
      when(camundaDataSource.getJsonMapper()).thenReturn(new CamundaObjectMapper());

      // then
      Assertions.assertThatThrownBy(
              () -> assertThat(DecisionSelectors.byName(NAME)).containsOutput("foo"))
          .hasMessage("Expected [name] to have output 'foo', but was 'outputValue'");

      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      Assertions.assertThatThrownBy(
              () -> assertThat(DecisionSelectors.byName(NAME)).hasOutput(expected))
          .hasMessage("Expected [name] to have output '{a=b}', but was 'outputValue'");
    }

    @Test
    public void hasOutputValueWithNoMatches() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers(null));
      when(camundaDataSource.getJsonMapper()).thenReturn(new CamundaObjectMapper());

      // then
      Assertions.assertThatThrownBy(
              () -> assertThat(DecisionSelectors.byName(NAME)).containsOutput("foo"))
          .hasMessage("Expected [name] to have output 'foo', but was '<None>'");

      final Map<String, Object> expected = new HashMap<>();
      expected.put("a", "b");
      Assertions.assertThatThrownBy(
              () -> assertThat(DecisionSelectors.byName(NAME)).hasOutput(expected))
          .hasMessage("Expected [name] to have output '{a=b}', but was '<None>'");
    }
  }

  @Nested
  public class HasMatchedRulesIndices {
    @Test
    public void hasMatched() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", singleRule()));

      // then
      assertThat(DecisionSelectors.byName(NAME)).hasMatchedRules(1);
    }

    @Test
    public void hasNotMatched() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", singleRule()));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThat(DecisionSelectors.byName(NAME)).hasMatchedRules(2))
          .hasMessage(
              "Expected [name] to have matched rule indexes [2], but did not. Matches:\n"
                  + "\t- matched: []\n"
                  + "\t- missing: [2]\n"
                  + "\t- unexpected: [1]");
    }

    @Test
    public void noMatchesExist() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue"));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThat(DecisionSelectors.byName(NAME)).hasMatchedRules(2))
          .hasMessage(
              "Expected [name] to have matched rule indexes [2], but did not. Matches:\n"
                  + "\t- matched: []\n"
                  + "\t- missing: [2]\n"
                  + "\t- unexpected: []");
    }

    @Test
    public void hasMatchedMultiple() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", multiRule()));

      // then
      assertThat(DecisionSelectors.byName(NAME)).hasMatchedRules(1, 2, 3);
    }

    @Test
    public void hasMatchedPartial() {
      // when
      mockDecisionInstanceSearch(decisionInstanceWithAnswers("outputValue", multiRule()));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThat(DecisionSelectors.byName(NAME)).hasMatchedRules(1, 2, 4))
          .hasMessage(
              "Expected [name] to have matched rule indexes [1, 2, 4], but did not. Matches:\n"
                  + "\t- matched: [1, 2]\n"
                  + "\t- missing: [4]\n"
                  + "\t- unexpected: [3]");
    }
  }

  private DecisionInstance decisionInstance() {
    return new DecisionInstanceImpl(
        new DecisionInstanceResult()
            .decisionDefinitionName(NAME)
            .decisionInstanceId(DECISION_INSTANCE_ID)
            .decisionInstanceKey(DECISION_INSTANCE_KEY)
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .processInstanceKey(PROCESS_INSTANCE_KEY)
            .decisionDefinitionKey(DECISION_DEFINITION_KEY)
            .decisionDefinitionVersion(DECISION_DEFINITION_VERSION),
        null);
  }

  private DecisionInstance decisionInstance(
      Function<DecisionInstanceResult, DecisionInstanceResult> resultBuilderFn) {
    DecisionInstanceResult basicResult =
        new DecisionInstanceResult()
            .decisionDefinitionName(NAME)
            .decisionInstanceId(DECISION_INSTANCE_ID)
            .decisionInstanceKey(DECISION_INSTANCE_KEY)
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .processInstanceKey(PROCESS_INSTANCE_KEY)
            .decisionDefinitionKey(DECISION_DEFINITION_KEY)
            .decisionDefinitionVersion(DECISION_DEFINITION_VERSION);

    return new DecisionInstanceImpl(resultBuilderFn.apply(basicResult), null);
  }

  private DecisionInstance decisionInstanceWithAnswers(
      final String result, final MatchedDecisionRule... rules) {
    List<MatchedDecisionRule> rulesList = Arrays.stream(rules).collect(Collectors.toList());

    return new DecisionInstanceImpl(
        null,
        Integer.parseInt(DECISION_INSTANCE_KEY),
        DECISION_INSTANCE_ID,
        DecisionInstanceState.EVALUATED,
        null,
        null,
        Long.parseLong(PROCESS_DEFINITION_KEY),
        Long.parseLong(PROCESS_INSTANCE_KEY),
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

  private MatchedDecisionRule rule(MatchedDecisionRuleItem ruleItem) {
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
}
