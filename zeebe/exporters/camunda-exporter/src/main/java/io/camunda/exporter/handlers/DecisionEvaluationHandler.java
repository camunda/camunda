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
import java.util.stream.IntStream;

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
    final CachedDecisions cached = getCachedDecisions(record);
    // Records written by older Zeebe versions have no decisionEvaluationInstanceKey (default "");
    // for them we fall back to the legacy ids derived from the record key and decision index.
    final List<String> instanceKeys =
        cached.decisions().stream()
            .map(EvaluatedDecisionValue::getDecisionEvaluationInstanceKey)
            .toList();
    return instanceKeys.contains("") ? cached.ids() : instanceKeys;
  }

  @Override
  public DecisionInstanceEntity createNewEntity(final String id) {
    return new DecisionInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<DecisionEvaluationRecordValue> record, final DecisionInstanceEntity entity) {
    final DecisionEvaluationRecordValue decisionEvaluation = record.getValue();
    final CachedDecisions cached = getCachedDecisions(record);
    final List<EvaluatedDecisionValue> evaluatedDecisions = cached.decisions();
    final int decisionIndex = cached.indexOf(entity.getId());
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

  private CachedDecisions getCachedDecisions(final Record<DecisionEvaluationRecordValue> record) {
    if (cachedDecisions == null || !cachedDecisions.isFor(record)) {
      cachedDecisions = CachedDecisions.of(record);
    }
    return cachedDecisions;
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

  /**
   * The evaluated decisions of one record, with their ids {@code <recordKey>-<index>} (1-based) in
   * evaluation order. The engine builds decisionEvaluationInstanceKey the same way, so these ids
   * resolve entities created from either the instance keys or the legacy ids.
   */
  private record CachedDecisions(
      int partitionId,
      long position,
      long key,
      List<EvaluatedDecisionValue> decisions,
      List<String> ids) {

    static CachedDecisions of(final Record<DecisionEvaluationRecordValue> record) {
      final List<EvaluatedDecisionValue> decisions = record.getValue().getEvaluatedDecisions();
      final List<String> ids =
          IntStream.rangeClosed(1, decisions.size())
              .mapToObj(i -> record.getKey() + "-" + i)
              .toList();
      return new CachedDecisions(
          record.getPartitionId(), record.getPosition(), record.getKey(), decisions, ids);
    }

    boolean isFor(final Record<DecisionEvaluationRecordValue> record) {
      return partitionId == record.getPartitionId()
          && position == record.getPosition()
          && key == record.getKey();
    }

    /** Returns the 1-based index of the decision exported under the given entity id. */
    int indexOf(final String entityId) {
      final int index = ids.indexOf(entityId);
      if (index < 0) {
        throw new IllegalStateException(
            "Decision evaluation %d has no evaluated decision with id %s (%d decisions)"
                .formatted(key, entityId, ids.size()));
      }
      return index + 1;
    }
  }
}
