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

import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstanceState;
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
  @Mock
  private CamundaDataSource camundaDataSource;

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

  @Test
  public void canChainMultipleAssertions() {
    // TODO
  }

  @Nested
  public class IsEvaluated {
    @Test
    public void isEvaluated() {
      // when
      when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  new DecisionInstanceImpl(
                      new DecisionInstanceResult()
                          .decisionDefinitionName("a")
                          .decisionInstanceId("a")
                          .decisionInstanceKey("1")
                          .processDefinitionKey("1")
                          .processInstanceKey("1")
                          .decisionDefinitionKey("1")
                          .decisionDefinitionVersion(1)
                          .state(DecisionInstanceStateEnum.EVALUATED),
                      null
                  )));

      // then
      assertThat(DecisionSelectors.byName("a")).isEvaluated();
    }
  }

  @Nested
  public class HasOutput {
    @Test
    public void hasOutputValueWithSingleMatch() {
      // when
      when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  new DecisionInstanceImpl(
                      null,
                      1L,
                      "decisionInstanceId",
                      DecisionInstanceState.EVALUATED,
                      null,
                      null,
                      1L,
                      1L,
                      1L,
                      "decisionDefinitionId",
                      "decisionDefinitionName",
                      1,
                      DecisionDefinitionType.DECISION_TABLE,
                      "tenantId",
                      Collections.emptyList(),
                      Collections.singletonList(
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId")
                                  .ruleIndex(1)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId")
                                          .outputName("outputName")
                                          .outputValue("outputValue")
                                  ), null)))));

      // then
      assertThat(DecisionSelectors.byName("decisionDefinitionName"))
          .hasOutput("outputValue");
    }

    @Test
    public void hasOutputValueWithMultipleMatch() {
      // when
      when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  new DecisionInstanceImpl(
                      null,
                      1L,
                      "decisionInstanceId",
                      DecisionInstanceState.EVALUATED,
                      null,
                      null,
                      1L,
                      1L,
                      1L,
                      "decisionDefinitionId",
                      "decisionDefinitionName",
                      1,
                      DecisionDefinitionType.DECISION_TABLE,
                      "tenantId",
                      Collections.emptyList(),
                      Arrays.asList(
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId")
                                  .ruleIndex(1)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId1")
                                          .outputName("outputName1")
                                          .outputValue("outputValue1")
                                  ), null),
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId2")
                                  .ruleIndex(2)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId2")
                                          .outputName("outputName2")
                                          .outputValue("outputValue2")
                                  ), null),
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId3")
                                  .ruleIndex(3)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId3")
                                          .outputName("outputName3")
                                          .outputValue("outputValue3")
                                  ), null)))));

      // then
      assertThat(DecisionSelectors.byName("decisionDefinitionName"))
          .hasOutput("outputValue2");
    }

    @Test
    public void hasOutputNameAndValueWithSingleMatch() {
      // when
      when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  new DecisionInstanceImpl(
                      null,
                      1L,
                      "decisionInstanceId",
                      DecisionInstanceState.EVALUATED,
                      null,
                      null,
                      1L,
                      1L,
                      1L,
                      "decisionDefinitionId",
                      "decisionDefinitionName",
                      1,
                      DecisionDefinitionType.DECISION_TABLE,
                      "tenantId",
                      Collections.emptyList(),
                      Collections.singletonList(
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId")
                                  .ruleIndex(1)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId")
                                          .outputName("outputName")
                                          .outputValue("outputValue")
                                  ), null)))));

      // then
      assertThat(DecisionSelectors.byName("decisionDefinitionName"))
          .hasOutput("outputName", "outputValue");
    }

    @Test
    public void hasOutputNameAndValueWithMultipleMatch() {
      // when
      when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  new DecisionInstanceImpl(
                      null,
                      1L,
                      "decisionInstanceId",
                      DecisionInstanceState.EVALUATED,
                      null,
                      null,
                      1L,
                      1L,
                      1L,
                      "decisionDefinitionId",
                      "decisionDefinitionName",
                      1,
                      DecisionDefinitionType.DECISION_TABLE,
                      "tenantId",
                      Collections.emptyList(),
                      Arrays.asList(
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId")
                                  .ruleIndex(1)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId1")
                                          .outputName("outputName1")
                                          .outputValue("outputValue1")
                                  ), null),
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId2")
                                  .ruleIndex(2)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId2")
                                          .outputName("outputName2")
                                          .outputValue("outputValue2")
                                  ), null),
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId3")
                                  .ruleIndex(3)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId3")
                                          .outputName("outputName3")
                                          .outputValue("outputValue3")
                                  ), null)))));

      // then
      assertThat(DecisionSelectors.byName("decisionDefinitionName"))
          .hasOutput("outputName2", "outputValue2");
    }

    @Test
    public void hasOutputNameAndValueDoesNotMixMatchedEvaluations() {
      // when
      when(camundaDataSource.findDecisionInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  new DecisionInstanceImpl(
                      null,
                      1L,
                      "decisionInstanceId",
                      DecisionInstanceState.EVALUATED,
                      null,
                      null,
                      1L,
                      1L,
                      1L,
                      "decisionDefinitionId",
                      "decisionDefinitionName",
                      1,
                      DecisionDefinitionType.DECISION_TABLE,
                      "tenantId",
                      Collections.emptyList(),
                      Arrays.asList(
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId")
                                  .ruleIndex(1)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId1")
                                          .outputName("outputName1")
                                          .outputValue("outputValue1")
                                  ), null),
                          new MatchedDecisionRuleImpl(
                              new MatchedDecisionRuleItem()
                                  .ruleId("ruleId2")
                                  .ruleIndex(2)
                                  .addEvaluatedOutputsItem(
                                      new EvaluatedDecisionOutputItem()
                                          .outputId("outputId2")
                                          .outputName("outputName2")
                                          .outputValue("outputValue2")
                                  ), null)))));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(DecisionSelectors.byName("a"))
              .hasOutput("outputName1", "outputValue2"));
              // TODO withMessage assertion
    }
  }
}
