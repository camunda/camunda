/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

import io.camunda.operate.entities.dmn.*;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionEvaluationHandler
    implements ExportHandler<DecisionInstanceEntity, DecisionEvaluationRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionEvaluationHandler.class);

  private final DecisionInstanceTemplate decisionInstanceTemplate;

  public DecisionEvaluationHandler(DecisionInstanceTemplate decisionInstanceTemplate) {
    this.decisionInstanceTemplate = decisionInstanceTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.DECISION_EVALUATION;
  }

  @Override
  public Class<DecisionInstanceEntity> getEntityType() {
    return DecisionInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<DecisionEvaluationRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(Record<DecisionEvaluationRecordValue> record) {
    final DecisionEvaluationRecordValue decisionEvaluation =
        (DecisionEvaluationRecordValue) record.getValue();
    final List<String> ids = new ArrayList<>();
    for (int i = 1; i <= decisionEvaluation.getEvaluatedDecisions().size(); i++) {
      final String id = (new DecisionInstanceEntity()).setId(record.getKey(), i).getId();
      ids.add(id);
    }
    return ids;
  }

  @Override
  public DecisionInstanceEntity createNewEntity(String id) {
    return new DecisionInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<DecisionEvaluationRecordValue> record, DecisionInstanceEntity entity) {

    final DecisionEvaluationRecordValue decisionEvaluation = record.getValue();
    for (int i = 1; i <= decisionEvaluation.getEvaluatedDecisions().size(); i++) {
      final String id = (new DecisionInstanceEntity()).setId(record.getKey(), i).getId();
      if (Objects.equals(entity.getId(), id)) {
        LOGGER.debug(
            "Decision evaluation: id {} key {}, decisionId {}",
            id,
            record.getKey(),
            decisionEvaluation.getDecisionId());

        final EvaluatedDecisionValue decision =
            decisionEvaluation.getEvaluatedDecisions().get(i - 1);
        final OffsetDateTime timestamp =
            DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
        final DecisionInstanceState state = getState(record, decisionEvaluation, i);
        entity
            .setKey(record.getKey())
            .setExecutionIndex(i)
            .setPosition(record.getPosition())
            .setPartitionId(record.getPartitionId())
            .setEvaluationDate(timestamp)
            .setProcessInstanceKey(decisionEvaluation.getProcessInstanceKey())
            .setProcessDefinitionKey(decisionEvaluation.getProcessDefinitionKey())
            .setBpmnProcessId(decisionEvaluation.getBpmnProcessId())
            .setElementInstanceKey(decisionEvaluation.getElementInstanceKey())
            .setElementId(decisionEvaluation.getElementId())
            .setDecisionRequirementsKey(decisionEvaluation.getDecisionRequirementsKey())
            .setDecisionRequirementsId(decisionEvaluation.getDecisionRequirementsId())
            .setRootDecisionId(decisionEvaluation.getDecisionId())
            .setRootDecisionName(decisionEvaluation.getDecisionName())
            .setRootDecisionDefinitionId(String.valueOf(decisionEvaluation.getDecisionKey()))
            .setDecisionId(decision.getDecisionId())
            .setDecisionDefinitionId(String.valueOf(decision.getDecisionKey()))
            .setDecisionType(DecisionType.fromZeebeDecisionType(decision.getDecisionType()))
            .setDecisionName(decision.getDecisionName())
            .setDecisionVersion((int) decision.getDecisionVersion())
            .setState(state)
            .setResult(decision.getDecisionOutput())
            .setEvaluatedOutputs(createEvaluationOutputs(decision.getMatchedRules()))
            .setEvaluatedInputs(createEvaluationInputs(decision.getEvaluatedInputs()))
            .setTenantId(tenantOrDefault(decisionEvaluation.getTenantId()));
        if (state.equals(DecisionInstanceState.FAILED)) {
          entity.setEvaluationFailure(decisionEvaluation.getEvaluationFailureMessage());
        }

        break;
      }
    }
  }

  @Override
  public void flush(DecisionInstanceEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(getIndexName(), entity);
  }

  @Override
  public String getIndexName() {
    return decisionInstanceTemplate.getFullQualifiedName();
  }

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

  private List<DecisionInstanceInputEntity> createEvaluationInputs(
      final List<EvaluatedInputValue> evaluatedInputs) {
    return evaluatedInputs.stream()
        .map(
            input ->
                new DecisionInstanceInputEntity()
                    .setId(input.getInputId())
                    .setName(input.getInputName())
                    .setValue(input.getInputValue()))
        .collect(Collectors.toList());
  }

  private List<DecisionInstanceOutputEntity> createEvaluationOutputs(
      final List<MatchedRuleValue> matchedRules) {
    final List<DecisionInstanceOutputEntity> outputs = new ArrayList<>();
    matchedRules.forEach(
        rule ->
            outputs.addAll(
                rule.getEvaluatedOutputs().stream()
                    .map(
                        output ->
                            new DecisionInstanceOutputEntity()
                                .setRuleId(rule.getRuleId())
                                .setRuleIndex(rule.getRuleIndex())
                                .setId(output.getOutputId())
                                .setName(output.getOutputName())
                                .setValue(output.getOutputValue()))
                    .toList()));
    return outputs;
  }
}
