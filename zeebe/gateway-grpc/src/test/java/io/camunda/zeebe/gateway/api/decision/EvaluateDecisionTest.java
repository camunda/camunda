/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.decision;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionResponse;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public class EvaluateDecisionTest extends GatewayTest {

  @Test
  public void shouldMapToBrokerRequest() {
    // given
    final DecisionEvaluationRecord evaluationRecord = EvaluateDecisionStub.createDecisionRecord();
    final EvaluateDecisionStub stub = new EvaluateDecisionStub();
    stub.registerWith(brokerClient);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final EvaluateDecisionRequest request =
        EvaluateDecisionRequest.newBuilder()
            .setDecisionId(evaluationRecord.getDecisionId())
            .setDecisionKey(evaluationRecord.getDecisionKey())
            .setVariables(variables)
            .build();

    // when
    client.evaluateDecision(request);

    // then
    final BrokerEvaluateDecisionRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.DECISION_EVALUATION);

    final DecisionEvaluationRecord record = brokerRequest.getRequestWriter();
    assertThat(record.getDecisionId()).isEqualTo(evaluationRecord.getDecisionId());
    assertThat(record.getDecisionKey()).isEqualTo(evaluationRecord.getDecisionKey());
    MsgPackUtil.assertEqualityExcluding(record.getVariablesBuffer(), variables);
    assertThat(record.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final DecisionEvaluationRecord evaluationRecord = EvaluateDecisionStub.createDecisionRecord();
    final EvaluateDecisionStub stub = new EvaluateDecisionStub();
    stub.registerWith(brokerClient);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final EvaluateDecisionRequest request =
        EvaluateDecisionRequest.newBuilder()
            .setDecisionId(evaluationRecord.getDecisionId())
            .setDecisionKey(evaluationRecord.getDecisionKey())
            .setVariables(variables)
            .build();

    // when
    final EvaluateDecisionResponse response = client.evaluateDecision(request);

    // then
    assertThat(response).isNotNull();

    // assert DecisionEvaluationRecord mapping
    assertThat(response.getDecisionInstanceKey()).isEqualTo(evaluationRecord.getDecisionKey());
    assertThat(response.getDecisionId()).isEqualTo(evaluationRecord.getDecisionId());
    assertThat(response.getDecisionKey()).isEqualTo(evaluationRecord.getDecisionKey());
    assertThat(response.getDecisionName()).isEqualTo(evaluationRecord.getDecisionName());
    assertThat(response.getDecisionVersion()).isEqualTo(evaluationRecord.getDecisionVersion());
    assertThat(response.getDecisionRequirementsId())
        .isEqualTo(evaluationRecord.getDecisionRequirementsId());
    assertThat(response.getDecisionRequirementsKey())
        .isEqualTo(evaluationRecord.getDecisionRequirementsKey());
    assertThat(response.getDecisionOutput()).isEqualTo(evaluationRecord.getDecisionOutput());
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // assert EvaluatedDecisionRecord mapping
    assertThat(response.getEvaluatedDecisionsCount()).isOne();
    final var intermediateResultResponse = response.getEvaluatedDecisions(0);
    final var expectedIntermediateDecisionResult = evaluationRecord.getEvaluatedDecisions().get(0);
    assertThat(intermediateResultResponse.getDecisionId())
        .isEqualTo(expectedIntermediateDecisionResult.getDecisionId());
    assertThat(intermediateResultResponse.getDecisionEvaluationInstanceKey())
        .isEqualTo(expectedIntermediateDecisionResult.getDecisionEvaluationInstanceKey());
    assertThat(intermediateResultResponse.getDecisionKey())
        .isEqualTo(expectedIntermediateDecisionResult.getDecisionKey());
    assertThat(intermediateResultResponse.getDecisionName())
        .isEqualTo(expectedIntermediateDecisionResult.getDecisionName());
    assertThat(intermediateResultResponse.getDecisionType())
        .isEqualTo(expectedIntermediateDecisionResult.getDecisionType());
    assertThat(intermediateResultResponse.getDecisionVersion())
        .isEqualTo(expectedIntermediateDecisionResult.getDecisionVersion());
    assertThat(intermediateResultResponse.getDecisionOutput())
        .isEqualTo(expectedIntermediateDecisionResult.getDecisionOutput());
    assertThat(intermediateResultResponse.getTenantId())
        .isEqualTo(expectedIntermediateDecisionResult.getTenantId());

    // assert EvaluatedInputRecord mapping
    assertThat(intermediateResultResponse.getEvaluatedInputsCount()).isOne();
    final var evaluatedInputResponse = intermediateResultResponse.getEvaluatedInputs(0);
    final var expectedEvaluatedDecisionInput =
        expectedIntermediateDecisionResult.getEvaluatedInputs().get(0);
    assertThat(evaluatedInputResponse.getInputId())
        .isEqualTo(expectedEvaluatedDecisionInput.getInputId());
    assertThat(evaluatedInputResponse.getInputName())
        .isEqualTo(expectedEvaluatedDecisionInput.getInputName());
    assertThat(evaluatedInputResponse.getInputValue())
        .isEqualTo(expectedEvaluatedDecisionInput.getInputValue());

    // assert MatchedRuleRecord mapping
    assertThat(intermediateResultResponse.getMatchedRulesCount()).isOne();
    final var matchedRuleResponse = intermediateResultResponse.getMatchedRules(0);
    final var expectedMatchedDecisionRule =
        expectedIntermediateDecisionResult.getMatchedRules().get(0);
    assertThat(matchedRuleResponse.getRuleId()).isEqualTo(expectedMatchedDecisionRule.getRuleId());
    assertThat(matchedRuleResponse.getRuleIndex())
        .isEqualTo(expectedMatchedDecisionRule.getRuleIndex());

    // assert EvaluatedOutputRecord mapping
    assertThat(matchedRuleResponse.getEvaluatedOutputsCount()).isOne();
    final var evaluatedOutputResponse = matchedRuleResponse.getEvaluatedOutputs(0);
    final var expectedEvaluatedDecisionOutput =
        expectedMatchedDecisionRule.getEvaluatedOutputs().get(0);
    assertThat(evaluatedOutputResponse.getOutputId())
        .isEqualTo(expectedEvaluatedDecisionOutput.getOutputId());
    assertThat(evaluatedOutputResponse.getOutputName())
        .isEqualTo(expectedEvaluatedDecisionOutput.getOutputName());
    assertThat(evaluatedOutputResponse.getOutputValue())
        .isEqualTo(expectedEvaluatedDecisionOutput.getOutputValue());
  }

  @Test
  public void shouldMapDecisionVersionToBrokerRequest() {
    // given
    final DecisionEvaluationRecord evaluationRecord = EvaluateDecisionStub.createDecisionRecord();
    final EvaluateDecisionStub stub = new EvaluateDecisionStub();
    stub.registerWith(brokerClient);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));
    final int decisionVersion = 2;

    final EvaluateDecisionRequest request =
        EvaluateDecisionRequest.newBuilder()
            .setDecisionId(evaluationRecord.getDecisionId())
            .setDecisionVersion(decisionVersion)
            .setVariables(variables)
            .build();

    // when
    client.evaluateDecision(request);

    // then
    final BrokerEvaluateDecisionRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    final DecisionEvaluationRecord record = brokerRequest.getRequestWriter();
    assertThat(record.getDecisionId()).isEqualTo(evaluationRecord.getDecisionId());
    assertThat(record.getDecisionVersion()).isEqualTo(decisionVersion);
    MsgPackUtil.assertEqualityExcluding(record.getVariablesBuffer(), variables);
  }

  @Test
  public void shouldMapDefaultDecisionVersionToBrokerRequest() {
    // given
    final DecisionEvaluationRecord evaluationRecord = EvaluateDecisionStub.createDecisionRecord();
    final EvaluateDecisionStub stub = new EvaluateDecisionStub();
    stub.registerWith(brokerClient);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final EvaluateDecisionRequest request =
        EvaluateDecisionRequest.newBuilder()
            .setDecisionId(evaluationRecord.getDecisionId())
            .setVariables(variables)
            .build();

    // when
    client.evaluateDecision(request);

    // then
    final BrokerEvaluateDecisionRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    final DecisionEvaluationRecord record = brokerRequest.getRequestWriter();
    assertThat(record.getDecisionVersion()).isEqualTo(-1);
  }
}
