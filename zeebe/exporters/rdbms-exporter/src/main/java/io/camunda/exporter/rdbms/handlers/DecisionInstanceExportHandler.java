/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedInput;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedOutput;
import io.camunda.db.rdbms.write.service.DecisionInstanceWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DecisionInstanceExportHandler
    implements RdbmsExportHandler<DecisionEvaluationRecordValue> {

  private final DecisionInstanceWriter decisionInstanceWriter;

  public DecisionInstanceExportHandler(final DecisionInstanceWriter decisionInstanceWriter) {
    this.decisionInstanceWriter = decisionInstanceWriter;
  }

  @Override
  public boolean canExport(final Record<DecisionEvaluationRecordValue> record) {
    return record.getValueType() == ValueType.DECISION_EVALUATION
        && record.getIntent() == DecisionEvaluationIntent.EVALUATED;
  }

  @Override
  public void export(final Record<DecisionEvaluationRecordValue> record) {
    final DecisionEvaluationRecordValue value = record.getValue();

    int index = 1;
    for (index = 1; index <= value.getEvaluatedDecisions().size(); index++) {
      final EvaluatedDecisionValue evaluatedDecision = value.getEvaluatedDecisions().get(index - 1);
      final DecisionInstanceDbModel decisionInstance = map(record, evaluatedDecision, index);
      decisionInstanceWriter.create(decisionInstance);
    }
  }

  // TODO move to common exporter util module #24002
  private DecisionInstanceState getState(
      final Record<DecisionEvaluationRecordValue> record,
      final DecisionEvaluationRecordValue decisionEvaluation,
      final int i) {
    if (record.getIntent().name().equals(DecisionEvaluationIntent.FAILED.name())
        && i == decisionEvaluation.getEvaluatedDecisions().size()) {
      return DecisionInstanceState.FAILED;
    } else {
      return DecisionInstanceState.EVALUATED;
    }
  }

  private DecisionInstanceDbModel map(
      final Record<DecisionEvaluationRecordValue> record,
      final EvaluatedDecisionValue evaluatedDecision,
      final int index) {
    final DecisionEvaluationRecordValue value = record.getValue();
    final var state = getState(record, value, index);
    final var key = record.getKey();
    final var id =
        !Objects.equals(evaluatedDecision.getDecisionEvaluationInstanceKey(), "")
            ? evaluatedDecision.getDecisionEvaluationInstanceKey()
            : String.format("%s-%d", key, index);

    return new DecisionInstanceDbModel.Builder()
        .decisionInstanceId(id)
        .decisionInstanceKey(key)
        .decisionDefinitionKey(evaluatedDecision.getDecisionKey())
        .decisionDefinitionId(evaluatedDecision.getDecisionId())
        .evaluationDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .processDefinitionKey(value.getProcessDefinitionKey())
        .processDefinitionId(value.getBpmnProcessId())
        .processInstanceKey(value.getProcessInstanceKey())
        .rootProcessInstanceKey(value.getRootProcessInstanceKey())
        .decisionRequirementsKey(value.getDecisionRequirementsKey())
        .decisionRequirementsId(value.getDecisionRequirementsId())
        .flowNodeInstanceKey(value.getElementInstanceKey())
        .flowNodeId(value.getElementId())
        .rootDecisionDefinitionKey(evaluatedDecision.getDecisionKey())
        .result(evaluatedDecision.getDecisionOutput())
        .evaluatedInputs(createEvaluationInputs(id, evaluatedDecision.getEvaluatedInputs()))
        .evaluatedOutputs(createEvaluationOutputs(id, evaluatedDecision.getMatchedRules()))
        .state(state)
        .decisionType(DecisionDefinitionType.fromValue(evaluatedDecision.getDecisionType()))
        .evaluationFailure(
            state == DecisionInstanceState.FAILED ? value.getEvaluationFailureMessage() : null)
        .partitionId(record.getPartitionId())
        .tenantId(value.getTenantId())
        .build();
  }

  private List<EvaluatedInput> createEvaluationInputs(
      final String id, final List<EvaluatedInputValue> evaluatedInputs) {
    return evaluatedInputs.stream()
        .map(
            input ->
                new EvaluatedInput(
                    id, input.getInputId(), input.getInputName(), input.getInputValue()))
        .collect(Collectors.toList());
  }

  private List<EvaluatedOutput> createEvaluationOutputs(
      final String id, final List<MatchedRuleValue> matchedRules) {
    final List<EvaluatedOutput> outputs = new ArrayList<>();
    matchedRules.forEach(
        rule ->
            outputs.addAll(
                rule.getEvaluatedOutputs().stream()
                    .map(
                        output ->
                            new EvaluatedOutput(
                                id,
                                output.getOutputId(),
                                output.getOutputName(),
                                output.getOutputValue(),
                                rule.getRuleId(),
                                rule.getRuleIndex()))
                    .toList()));
    return outputs;
  }
}
