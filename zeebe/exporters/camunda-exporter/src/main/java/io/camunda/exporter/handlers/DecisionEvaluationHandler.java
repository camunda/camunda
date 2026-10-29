/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import io.camunda.zeebe.exporter.common.utils.StorageOrdinalUtil;
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
import java.util.stream.Collectors;

public class DecisionEvaluationHandler
    implements OrdinalIndexExportHandler<DecisionInstanceEntity, DecisionEvaluationRecordValue> {

  private final String indexName;

  // getEvaluatedDecisions() deep-copies every decision on each call, and updateEntity is invoked
  // once per evaluated decision of the same record. Reusing the list for the current record keeps
  // the export cost linear in the number of decisions instead of quadratic.
  private CachedDecisions cachedDecisions;

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
    // To ensure compatibility with older Zeebe versions, we first check if the IDs are already
    // present in the record. If they are, we use them directly. If not,
    // we fall back to the legacy ID generation method.
    final var ids =
        getCachedEvaluatedDecisions(record).stream()
            .map(EvaluatedDecisionValue::getDecisionEvaluationInstanceKey)
            .toList();
    // If none of the IDs are empty, we can safely return the list.
    // "" is the default value for the decisionEvaluationInstanceKey field.
    if (!ids.contains("")) {
      return ids;
    }
    return generateIdsLegacy(record);
  }

  @Override
  public DecisionInstanceEntity createNewEntity(final String id) {
    return new DecisionInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<DecisionEvaluationRecordValue> record, final DecisionInstanceEntity entity) {
    final DecisionEvaluationRecordValue decisionEvaluation = record.getValue();
    final List<EvaluatedDecisionValue> evaluatedDecisions = getCachedEvaluatedDecisions(record);
    final int decisionIndex = getDecisionIndex(record.getKey(), entity.getId(), evaluatedDecisions);
    if (decisionIndex == evaluatedDecisions.size()) {
      // the last decision of the record is exported; release the copies
      cachedDecisions = null;
    }

    final EvaluatedDecisionValue decision = evaluatedDecisions.get(decisionIndex - 1);
    final OffsetDateTime timestamp =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC);
    final DecisionInstanceState state = getState(record, evaluatedDecisions.size(), decisionIndex);
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
        .setDecisionType(DecisionType.fromString(decision.getDecisionType()))
        .setDecisionName(decision.getDecisionName())
        .setDecisionVersion(decision.getDecisionVersion())
        .setState(state)
        .setResult(decision.getDecisionOutput())
        .setEvaluatedOutputs(createEvaluationOutputs(decision.getMatchedRules()))
        .setEvaluatedInputs(createEvaluationInputs(decision.getEvaluatedInputs()))
        .setTenantId(ExporterUtil.tenantOrDefault(decisionEvaluation.getTenantId()))
        .setStorageOrdinal(StorageOrdinalUtil.ordinalIfOrdinalControlled(decisionEvaluation));
    if (state.equals(DecisionInstanceState.FAILED)) {
      entity.setEvaluationFailureMessage(decisionEvaluation.getEvaluationFailureMessage());
    }
    final var rootProcessInstanceKey = decisionEvaluation.getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      entity.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
    final String businessId = decisionEvaluation.getBusinessId();
    if (!ExporterUtil.isEmpty(businessId)) {
      entity.setBusinessId(businessId);
    }
  }

  @Override
  public void flush(
      final TargetIndex index,
      final DecisionInstanceEntity entity,
      final BatchRequest batchRequest) {
    batchRequest.add(index, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private List<EvaluatedDecisionValue> getCachedEvaluatedDecisions(
      final Record<DecisionEvaluationRecordValue> record) {
    if (cachedDecisions == null || !cachedDecisions.isFor(record)) {
      cachedDecisions = CachedDecisions.of(record);
    }
    return cachedDecisions.decisions();
  }

  private static int getDecisionIndex(
      final long key, final String entityId, final List<EvaluatedDecisionValue> decisions) {
    for (int i = 1; i <= decisions.size(); i++) {
      if (formatId(key, i).equals(entityId)) {
        return i;
      }
    }
    throw new IllegalStateException(
        "Decision evaluation %d has no evaluated decision with id %s (%d decisions)"
            .formatted(key, entityId, decisions.size()));
  }

  private static String formatId(final long key, final int index) {
    return key + "-" + index;
  }

  private DecisionInstanceState getState(
      final Record<DecisionEvaluationRecordValue> record,
      final int evaluatedDecisionsCount,
      final int i) {
    if (record.getIntent().name().equals(DecisionEvaluationIntent.FAILED.name())
        && i == evaluatedDecisionsCount) {
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

  private List<String> generateIdsLegacy(final Record<DecisionEvaluationRecordValue> record) {
    final var ids = new ArrayList<String>();
    final int evaluatedDecisionsCount = getCachedEvaluatedDecisions(record).size();
    for (int i = 1; i <= evaluatedDecisionsCount; i++) {
      ids.add(formatId(record.getKey(), i));
    }
    return ids;
  }

  private record CachedDecisions(
      int partitionId, long position, long key, List<EvaluatedDecisionValue> decisions) {

    static CachedDecisions of(final Record<DecisionEvaluationRecordValue> record) {
      return new CachedDecisions(
          record.getPartitionId(),
          record.getPosition(),
          record.getKey(),
          record.getValue().getEvaluatedDecisions());
    }

    boolean isFor(final Record<DecisionEvaluationRecordValue> record) {
      return partitionId == record.getPartitionId()
          && position == record.getPosition()
          && key == record.getKey();
    }
  }
}
