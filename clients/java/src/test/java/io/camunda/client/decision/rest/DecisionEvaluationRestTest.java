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
package io.camunda.client.decision.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.EvaluatedDecision;
import io.camunda.client.api.response.EvaluatedDecisionInput;
import io.camunda.client.api.response.EvaluatedDecisionOutput;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.protocol.rest.DecisionEvaluationInstruction;
import io.camunda.client.protocol.rest.EvaluateDecisionResult;
import io.camunda.client.protocol.rest.EvaluatedDecisionInputItem;
import io.camunda.client.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.client.protocol.rest.EvaluatedDecisionResult;
import io.camunda.client.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DecisionEvaluationRestTest extends ClientRestTest {

  private static final long DECISION_KEY = 123L;
  private static final long DECISION_INSTANCE_KEY = 234L;
  private static final String TENANT_ID = "foo";
  private static final EvaluatedDecisionInputItem EVALUATED_INPUT =
      new EvaluatedDecisionInputItem()
          .inputId("input-id")
          .inputName("input-name")
          .inputValue("input-value");
  private static final EvaluatedDecisionOutputItem EVALUATED_OUTPUT =
      new EvaluatedDecisionOutputItem()
          .outputId("output-id")
          .outputName("output-name")
          .outputValue("output-value");
  private static final MatchedDecisionRuleItem MATCHED_RULE =
      new MatchedDecisionRuleItem()
          .ruleId("rule-id")
          .ruleIndex(1)
          .addEvaluatedOutputsItem(EVALUATED_OUTPUT);
  private static final EvaluatedDecisionResult EVALUATED_DECISION =
      new EvaluatedDecisionResult()
          .decisionDefinitionId("my-decision")
          .decisionDefinitionKey(String.valueOf(DECISION_KEY))
          .decisionDefinitionName("My Decision")
          .decisionDefinitionVersion(1)
          .decisionDefinitionType("TABLE")
          .output("testOutput")
          .tenantId(TENANT_ID)
          .addEvaluatedInputsItem(EVALUATED_INPUT)
          .addMatchedRulesItem(MATCHED_RULE);
  private static final EvaluateDecisionResult EVALUATE_DECISION_RESPONSE =
      new EvaluateDecisionResult()
          .decisionDefinitionKey(String.valueOf(DECISION_KEY))
          .decisionDefinitionId("my-decision")
          .decisionDefinitionName("My Decision")
          .decisionDefinitionVersion(1)
          .output("testOutput")
          .decisionRequirementsId("decision-requirements-id")
          .decisionRequirementsKey("124")
          .failedDecisionDefinitionId("my-decision")
          .failureMessage("decision-evaluation-failure")
          .tenantId(TENANT_ID)
          .decisionInstanceKey(String.valueOf(DECISION_INSTANCE_KEY))
          .decisionEvaluationKey(String.valueOf(DECISION_INSTANCE_KEY))
          .addEvaluatedDecisionsItem(EVALUATED_DECISION);

  @Test
  public void shouldEvaluateStandaloneDecisionWithDecisionKey() {
    // given
    gatewayService.onEvaluateDecisionRequest(EVALUATE_DECISION_RESPONSE);

    // when
    final EvaluateDecisionResponse response =
        client.newEvaluateDecisionCommand().decisionKey(DECISION_KEY).send().join();

    // then
    assertResponse(response);
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithDecisionId() {
    // given
    gatewayService.onEvaluateDecisionRequest(EVALUATE_DECISION_RESPONSE);

    // when
    final EvaluateDecisionResponse response =
        client.newEvaluateDecisionCommand().decisionId("my-decision").send().join();

    // then
    assertResponse(response);
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithStringVariables() {
    // given
    client
        .newEvaluateDecisionCommand()
        .decisionKey(DECISION_KEY)
        .variables("{\"foo\": \"bar\"}")
        .send()
        .join();

    // when
    final DecisionEvaluationInstruction request =
        gatewayService.getLastRequest(DecisionEvaluationInstruction.class);

    // then
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\": \"bar\"}";
    final InputStream inputStream =
        new ByteArrayInputStream(variables.getBytes(StandardCharsets.UTF_8));
    client
        .newEvaluateDecisionCommand()
        .decisionKey(DECISION_KEY)
        .variables(inputStream)
        .send()
        .join();

    // when
    final DecisionEvaluationInstruction request =
        gatewayService.getLastRequest(DecisionEvaluationInstruction.class);

    // then
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithMapVariables() {
    // given
    client
        .newEvaluateDecisionCommand()
        .decisionKey(DECISION_KEY)
        .variables(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // when
    final DecisionEvaluationInstruction request =
        gatewayService.getLastRequest(DecisionEvaluationInstruction.class);

    // then
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithObjectVariables() {
    // given
    client
        .newEvaluateDecisionCommand()
        .decisionKey(DECISION_KEY)
        .variables(new DecisionEvaluationRestTest.VariableDocument())
        .send()
        .join();

    // when
    final DecisionEvaluationInstruction request =
        gatewayService.getLastRequest(DecisionEvaluationInstruction.class);

    // then
    assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithSingleVariable() {
    // given
    final String key = "key";
    final String value = "value";
    client
        .newEvaluateDecisionCommand()
        .decisionKey(DECISION_KEY)
        .variable(key, value)
        .send()
        .join();

    // when
    final DecisionEvaluationInstruction request =
        gatewayService.getLastRequest(DecisionEvaluationInstruction.class);

    // then
    assertThat(request.getVariables()).containsOnly(entry(key, value));
  }

  @Test
  public void shouldThrowErrorWhenTryToEvaluateStandaloneDecisionWithNullVariable() {
    // when
    Assertions.assertThatThrownBy(
            () ->
                client
                    .newEvaluateDecisionCommand()
                    .decisionKey(DECISION_KEY)
                    .variable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRaise() {
    // when
    gatewayService.errorOnRequest(
        RestGatewayPaths.getEvaluateDecisionUrl(),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // then
    assertThatThrownBy(
            () -> client.newEvaluateDecisionCommand().decisionKey(DECISION_KEY).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByDecisionId() {
    // given
    final String tenantId = "test-tenant";

    // when
    client.newEvaluateDecisionCommand().decisionId("dmn").tenantId(tenantId).send().join();

    // then
    final DecisionEvaluationInstruction request =
        gatewayService.getLastRequest(DecisionEvaluationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByDecisionKey() {
    // given
    final String tenantId = "test-tenant";

    // when
    client.newEvaluateDecisionCommand().decisionKey(1L).tenantId(tenantId).send().join();

    // then
    final DecisionEvaluationInstruction request =
        gatewayService.getLastRequest(DecisionEvaluationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  private void assertResponse(final EvaluateDecisionResponse response) {
    // assert EvaluateDecisionResponse properties
    assertThat(String.valueOf(response.getDecisionKey()))
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getDecisionDefinitionKey());
    assertThat(response.getDecisionId())
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getDecisionDefinitionId());
    assertThat(response.getDecisionVersion())
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getDecisionDefinitionVersion());
    assertThat(response.getDecisionName())
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getDecisionDefinitionName());
    assertThat(response.getDecisionOutput()).isEqualTo(EVALUATE_DECISION_RESPONSE.getOutput());
    assertThat(response.getDecisionRequirementsId())
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getDecisionRequirementsId());
    assertThat(String.valueOf(response.getDecisionRequirementsKey()))
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getDecisionRequirementsKey());
    assertThat(response.getFailedDecisionId())
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getFailedDecisionDefinitionId());
    assertThat(response.getFailureMessage())
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getFailureMessage());
    assertThat(response.getTenantId()).isEqualTo(EVALUATE_DECISION_RESPONSE.getTenantId());
    assertThat(String.valueOf(response.getDecisionInstanceKey()))
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getDecisionInstanceKey());
    assertThat(String.valueOf(response.getDecisionEvaluationKey()))
        .isEqualTo(EVALUATE_DECISION_RESPONSE.getDecisionEvaluationKey());

    // assert EvaluatedDecision
    assertThat(response.getEvaluatedDecisions()).hasSize(1);
    final EvaluatedDecision evaluatedDecisionResponse = response.getEvaluatedDecisions().get(0);
    assertThat(evaluatedDecisionResponse.getDecisionId())
        .isEqualTo(EVALUATED_DECISION.getDecisionDefinitionId());
    assertThat(String.valueOf(evaluatedDecisionResponse.getDecisionKey()))
        .isEqualTo(EVALUATED_DECISION.getDecisionDefinitionKey());
    assertThat(evaluatedDecisionResponse.getDecisionName())
        .isEqualTo(EVALUATED_DECISION.getDecisionDefinitionName());
    assertThat(evaluatedDecisionResponse.getDecisionVersion())
        .isEqualTo(EVALUATED_DECISION.getDecisionDefinitionVersion());
    assertThat(evaluatedDecisionResponse.getDecisionType())
        .isEqualTo(EVALUATED_DECISION.getDecisionDefinitionType());
    assertThat(evaluatedDecisionResponse.getDecisionOutput())
        .isEqualTo(EVALUATED_DECISION.getOutput());
    assertThat(evaluatedDecisionResponse.getTenantId()).isEqualTo(EVALUATED_DECISION.getTenantId());

    // assert EvaluatedDecisionInput
    assertThat(evaluatedDecisionResponse.getEvaluatedInputs()).hasSize(1);
    final EvaluatedDecisionInput evaluatedDecisionInput =
        evaluatedDecisionResponse.getEvaluatedInputs().get(0);
    assertThat(evaluatedDecisionInput.getInputId()).isEqualTo(EVALUATED_INPUT.getInputId());
    assertThat(evaluatedDecisionInput.getInputName()).isEqualTo(EVALUATED_INPUT.getInputName());
    assertThat(evaluatedDecisionInput.getInputValue()).isEqualTo(EVALUATED_INPUT.getInputValue());

    // assert MatchedRule
    assertThat(evaluatedDecisionResponse.getMatchedRules()).hasSize(1);
    final MatchedDecisionRule matchedDecisionRule =
        evaluatedDecisionResponse.getMatchedRules().get(0);
    assertThat(matchedDecisionRule.getRuleId()).isEqualTo(MATCHED_RULE.getRuleId());
    assertThat(matchedDecisionRule.getRuleIndex()).isEqualTo(MATCHED_RULE.getRuleIndex());

    // assert EvaluatedDecisionOutput
    assertThat(matchedDecisionRule.getEvaluatedOutputs()).hasSize(1);
    final EvaluatedDecisionOutput evaluatedDecisionOutput =
        matchedDecisionRule.getEvaluatedOutputs().get(0);
    assertThat(evaluatedDecisionOutput.getOutputId()).isEqualTo(EVALUATED_OUTPUT.getOutputId());
    assertThat(evaluatedDecisionOutput.getOutputName()).isEqualTo(EVALUATED_OUTPUT.getOutputName());
    assertThat(evaluatedDecisionOutput.getOutputValue())
        .isEqualTo(EVALUATED_OUTPUT.getOutputValue());
  }

  public static class VariableDocument {

    VariableDocument() {}

    public String getFoo() {
      return "bar";
    }
  }
}
