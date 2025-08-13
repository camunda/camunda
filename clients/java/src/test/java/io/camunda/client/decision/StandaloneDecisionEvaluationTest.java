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
package io.camunda.client.decision;

import static io.camunda.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.EvaluatedDecision;
import io.camunda.client.api.response.EvaluatedDecisionInput;
import io.camunda.client.api.response.EvaluatedDecisionOutput;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Test;

public class StandaloneDecisionEvaluationTest extends ClientTest {

  private static final long DECISION_KEY = 123L;
  private static final long DECISION_INSTANCE_KEY = 234L;
  private static final String TENANT_ID = "foo";
  private static GatewayOuterClass.EvaluatedDecisionOutput evaluatedOutput;
  private static GatewayOuterClass.MatchedDecisionRule matchedRule;
  private static GatewayOuterClass.EvaluatedDecisionInput evaluatedInput;
  private static GatewayOuterClass.EvaluatedDecision evaluatedDecision;
  private static GatewayOuterClass.EvaluateDecisionResponse evaluateDecisionResponse;

  @BeforeClass
  public static void setUpAll() {
    evaluatedOutput =
        GatewayOuterClass.EvaluatedDecisionOutput.newBuilder()
            .setOutputId("output-id")
            .setOutputName("output-name")
            .setOutputValue("output-value")
            .build();

    matchedRule =
        GatewayOuterClass.MatchedDecisionRule.newBuilder()
            .setRuleId("rule-id")
            .setRuleIndex(1)
            .addEvaluatedOutputs(evaluatedOutput)
            .build();

    evaluatedInput =
        GatewayOuterClass.EvaluatedDecisionInput.newBuilder()
            .setInputId("input-id")
            .setInputName("input-name")
            .setInputValue("input-value")
            .build();

    evaluatedDecision =
        GatewayOuterClass.EvaluatedDecision.newBuilder()
            .setDecisionId("my-decision")
            .setDecisionKey(DECISION_KEY)
            .setDecisionName("My Decision")
            .setDecisionVersion(1)
            .setDecisionType("TABLE")
            .setDecisionOutput("testOutput")
            .setTenantId(TENANT_ID)
            .addEvaluatedInputs(evaluatedInput)
            .addMatchedRules(matchedRule)
            .build();

    evaluateDecisionResponse =
        GatewayOuterClass.EvaluateDecisionResponse.newBuilder()
            .setDecisionKey(DECISION_KEY)
            .setDecisionId("my-decision")
            .setDecisionName("My Decision")
            .setDecisionVersion(1)
            .setDecisionOutput("testOutput")
            .setDecisionRequirementsId("decision-requirements-id")
            .setDecisionRequirementsKey(124L)
            .setFailedDecisionId("my-decision")
            .setFailureMessage("decision-evaluation-failure")
            .setTenantId(TENANT_ID)
            .setDecisionInstanceKey(DECISION_INSTANCE_KEY)
            .addEvaluatedDecisions(evaluatedDecision)
            .build();
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithDecisionKey() {
    // given
    gatewayService.onEvaluateDecisionRequest(evaluateDecisionResponse);

    // when
    final EvaluateDecisionResponse response =
        client.newEvaluateDecisionCommand().decisionKey(DECISION_KEY).send().join();

    // then
    assertResponse(response);
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithDecisionId() {
    // given
    gatewayService.onEvaluateDecisionRequest(evaluateDecisionResponse);

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
    final EvaluateDecisionRequest request = gatewayService.getLastRequest();

    // then
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
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
    final EvaluateDecisionRequest request = gatewayService.getLastRequest();

    // then
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
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
    final EvaluateDecisionRequest request = gatewayService.getLastRequest();

    // then
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldEvaluateStandaloneDecisionWithObjectVariables() {
    // given
    client
        .newEvaluateDecisionCommand()
        .decisionKey(DECISION_KEY)
        .variables(new VariableDocument())
        .send()
        .join();

    // when
    final EvaluateDecisionRequest request = gatewayService.getLastRequest();

    // then
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("foo", "bar"));
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
    final EvaluateDecisionRequest request = gatewayService.getLastRequest();

    // then
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry(key, value));
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
        EvaluateDecisionRequest.class, () -> new ClientException("Invalid request"));

    // then
    assertThatThrownBy(
            () -> client.newEvaluateDecisionCommand().decisionKey(DECISION_KEY).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newEvaluateDecisionCommand()
        .decisionKey(DECISION_KEY)
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldUseDefaultTenantId() {
    // when
    client.newEvaluateDecisionCommand().decisionId("dmn").send().join();

    // then
    final EvaluateDecisionRequest request = gatewayService.getLastRequest();
    assertThat(request.getTenantId()).isEqualTo(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByDecisionId() {
    // given
    final String tenantId = "test-tenant";

    // when
    client.newEvaluateDecisionCommand().decisionId("dmn").tenantId(tenantId).send().join();

    // then
    final EvaluateDecisionRequest request = gatewayService.getLastRequest();
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByDecisionKey() {
    // given
    final String tenantId = "test-tenant";

    // when
    client.newEvaluateDecisionCommand().decisionKey(1L).tenantId(tenantId).send().join();

    // then
    final EvaluateDecisionRequest request = gatewayService.getLastRequest();
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  private void assertResponse(final EvaluateDecisionResponse response) {
    // assert EvaluateDecisionResponse properties
    assertThat(response.getDecisionKey()).isEqualTo(evaluateDecisionResponse.getDecisionKey());
    assertThat(response.getDecisionId()).isEqualTo(evaluateDecisionResponse.getDecisionId());
    assertThat(response.getDecisionVersion())
        .isEqualTo(evaluateDecisionResponse.getDecisionVersion());
    assertThat(response.getDecisionName()).isEqualTo(evaluateDecisionResponse.getDecisionName());
    assertThat(response.getDecisionOutput())
        .isEqualTo(evaluateDecisionResponse.getDecisionOutput());
    assertThat(response.getDecisionRequirementsId())
        .isEqualTo(evaluateDecisionResponse.getDecisionRequirementsId());
    assertThat(response.getDecisionRequirementsKey())
        .isEqualTo(evaluateDecisionResponse.getDecisionRequirementsKey());
    assertThat(response.getFailedDecisionId())
        .isEqualTo(evaluateDecisionResponse.getFailedDecisionId());
    assertThat(response.getFailureMessage())
        .isEqualTo(evaluateDecisionResponse.getFailureMessage());
    assertThat(response.getTenantId()).isEqualTo(evaluateDecisionResponse.getTenantId());
    assertThat(response.getDecisionInstanceKey())
        .isEqualTo(evaluateDecisionResponse.getDecisionInstanceKey());
    assertThat(response.getDecisionEvaluationKey())
        .isEqualTo(evaluateDecisionResponse.getDecisionEvaluationKey());

    // assert EvaluatedDecision
    assertThat(response.getEvaluatedDecisions()).hasSize(1);
    final EvaluatedDecision evaluatedDecisionResponse = response.getEvaluatedDecisions().get(0);
    assertThat(evaluatedDecisionResponse.getDecisionId())
        .isEqualTo(evaluatedDecision.getDecisionId());
    assertThat(evaluatedDecisionResponse.getDecisionKey())
        .isEqualTo(evaluatedDecision.getDecisionKey());
    assertThat(evaluatedDecisionResponse.getDecisionName())
        .isEqualTo(evaluatedDecision.getDecisionName());
    assertThat(evaluatedDecisionResponse.getDecisionVersion())
        .isEqualTo(evaluatedDecision.getDecisionVersion());
    assertThat(evaluatedDecisionResponse.getDecisionType())
        .isEqualTo(evaluatedDecision.getDecisionType());
    assertThat(evaluatedDecisionResponse.getDecisionOutput())
        .isEqualTo(evaluatedDecision.getDecisionOutput());
    assertThat(evaluatedDecisionResponse.getTenantId()).isEqualTo(evaluatedDecision.getTenantId());

    // assert EvaluatedDecisionInput
    assertThat(evaluatedDecisionResponse.getEvaluatedInputs()).hasSize(1);
    final EvaluatedDecisionInput evaluatedDecisionInput =
        evaluatedDecisionResponse.getEvaluatedInputs().get(0);
    assertThat(evaluatedDecisionInput.getInputId()).isEqualTo(evaluatedInput.getInputId());
    assertThat(evaluatedDecisionInput.getInputName()).isEqualTo(evaluatedInput.getInputName());
    assertThat(evaluatedDecisionInput.getInputValue()).isEqualTo(evaluatedInput.getInputValue());

    // assert MatchedRule
    assertThat(evaluatedDecisionResponse.getMatchedRules()).hasSize(1);
    final MatchedDecisionRule matchedDecisionRule =
        evaluatedDecisionResponse.getMatchedRules().get(0);
    assertThat(matchedDecisionRule.getRuleId()).isEqualTo(matchedRule.getRuleId());
    assertThat(matchedDecisionRule.getRuleIndex()).isEqualTo(matchedRule.getRuleIndex());

    // assert EvaluatedDecisionOutput
    assertThat(matchedDecisionRule.getEvaluatedOutputs()).hasSize(1);
    final EvaluatedDecisionOutput evaluatedDecisionOutput =
        matchedDecisionRule.getEvaluatedOutputs().get(0);
    assertThat(evaluatedDecisionOutput.getOutputId()).isEqualTo(evaluatedOutput.getOutputId());
    assertThat(evaluatedDecisionOutput.getOutputName()).isEqualTo(evaluatedOutput.getOutputName());
    assertThat(evaluatedDecisionOutput.getOutputValue())
        .isEqualTo(evaluatedOutput.getOutputValue());
  }

  public static class VariableDocument {

    VariableDocument() {}

    public String getFoo() {
      return "bar";
    }
  }
}
