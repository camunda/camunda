/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.decision;

import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateDecisionRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.EvaluatedDecisionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class EvaluateDecisionStub
    implements RequestStub<
        BrokerEvaluateDecisionRequest, BrokerResponse<DecisionEvaluationRecord>> {

  public static final DecisionEvaluationRecord DECISION_RECORD = new DecisionEvaluationRecord();

  {
    DECISION_RECORD
        .setDecisionId("decision")
        .setDecisionKey(123L)
        .setDecisionVersion(1)
        .setDecisionName("Decision")
        .setDecisionOutput(toMessagePack("\"decision-output\""))
        .setDecisionRequirementsId("decisionRequirements")
        .setDecisionRequirementsKey(124L);
    final EvaluatedDecisionRecord evaluatedDecisionRecord =
        DECISION_RECORD.evaluatedDecisions().add();
    evaluatedDecisionRecord
        .setDecisionId("intermediateDecision")
        .setDecisionKey(125L)
        .setDecisionName("Intermediate Decision")
        .setDecisionVersion(2)
        .setDecisionType("DECISION_TABLE")
        .setDecisionOutput(toMessagePack("\"intermediate-output\""))
        .evaluatedInputs()
        .add()
        .setInputId("inputId")
        .setInputName("INPUT NAME")
        .setInputValue(toMessagePack("\"input-value\""));
    evaluatedDecisionRecord
        .matchedRules()
        .add()
        .setRuleId("rule id")
        .setRuleIndex(1)
        .evaluatedOutputs()
        .add()
        .setOutputId("outputId")
        .setOutputName("OUTPUT NAME")
        .setOutputValue(toMessagePack("\"output-value\""));
  }

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerEvaluateDecisionRequest.class, this);
  }

  @Override
  public BrokerResponse<DecisionEvaluationRecord> handle(
      final BrokerEvaluateDecisionRequest request) throws Exception {
    return new BrokerResponse<>(DECISION_RECORD, request.getPartitionId(), request.getKey());
  }

  private static DirectBuffer toMessagePack(final String json) {
    final byte[] messagePack = MsgPackConverter.convertToMsgPack(json);
    return BufferUtil.wrapArray(messagePack);
  }
}
