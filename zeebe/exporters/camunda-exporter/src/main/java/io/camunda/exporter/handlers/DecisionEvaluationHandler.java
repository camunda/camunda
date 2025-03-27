/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionEvaluationHandler
    implements ExportHandler<DecisionInstanceEntity, DecisionEvaluationRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionEvaluationHandler.class);
  private static final String ID_PATTERN = "%s-%d";
  private final String indexName;

  public DecisionEvaluationHandler(final String indexName) {
    this.indexName = indexName;
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
  public boolean handlesRecord(final Record<DecisionEvaluationRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(final Record<DecisionEvaluationRecordValue> record) {
    final List<String> ids = new ArrayList<>();
    for (int i = 1; i <= record.getValue().getEvaluatedDecisions().size(); i++) {
      final String id = ID_PATTERN.formatted(record.getKey(), i);
      ids.add(id);
    }
    return ids;
  }

  @Override
  public DecisionInstanceEntity createNewEntity(final String id) {
    return new DecisionInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<DecisionEvaluationRecordValue> record, final DecisionInstanceEntity entity) {
    final DecisionEvaluationRecordValue decisionEvaluation = record.getValue();
    final int decisionIndex =
        getEvaluatedDecisionValueIndex(decisionEvaluation, record.getKey(), entity.getId());

    final EvaluatedDecisionValue decision =
        decisionEvaluation.getEvaluatedDecisions().get(decisionIndex - 1);
    final OffsetDateTime timestamp =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC);
    final DecisionInstanceState state = getState(record, decisionEvaluation, decisionIndex);
    entity
        .setKey(record.getKey())
        .setExecutionIndex(decisionIndex)
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
        .setDecisionVersion(decision.getDecisionVersion())
        .setState(state)
        .setResult(decision.getDecisionOutput())
        .setEvaluatedOutputs(createEvaluationOutputs(decision.getMatchedRules()))
        .setEvaluatedInputs(createEvaluationInputs(decision.getEvaluatedInputs()))
        .setTenantId(ExporterUtil.tenantOrDefault(decisionEvaluation.getTenantId()));
    if (state.equals(DecisionInstanceState.FAILED)) {
      entity.setEvaluationFailure(decisionEvaluation.getEvaluationFailureMessage());
    }
  }

  @Override
  public void flush(final DecisionInstanceEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private int getEvaluatedDecisionValueIndex(
      final DecisionEvaluationRecordValue decisionEvaluation,
      final long key,
      final String entityId) {
    for (int i = 1; i <= decisionEvaluation.getEvaluatedDecisions().size(); i++) {
      final String id = ID_PATTERN.formatted(key, i);
      if (Objects.equals(entityId, id)) {
        LOGGER.debug(
            "Decision evaluation: id {} key {}, decisionId {}",
            id,
            key,
            decisionEvaluation.getDecisionId());

        return i;
      }
    }
    LOGGER.warn(
        "Decision evaluation: id {} not found in evaluated decisions {}",
        entityId,
        decisionEvaluation.getEvaluatedDecisions());
    return 0;
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
