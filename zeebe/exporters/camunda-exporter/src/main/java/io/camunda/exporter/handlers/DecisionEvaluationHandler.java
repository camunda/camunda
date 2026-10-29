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
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    return getCachedDecisions(record).ids();
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
    if (cached.markExported(decisionIndex)) {
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
      cachedDecisions = new CachedDecisions(record);
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
   * The evaluated decisions of one record and their entity ids. Each decision resolves by both its
   * decisionEvaluationInstanceKey and its legacy id {@code <recordKey>-<index>} (1-based); records
   * written by older Zeebe versions have no instance key (default ""), so their entities use the
   * legacy ids. Tracks which decisions were exported, regardless of order or repetition, so the
   * copies can be released once every decision is done.
   */
  private static final class CachedDecisions {
    private final int partitionId;
    private final long position;
    private final long key;
    private final List<EvaluatedDecisionValue> decisions;
    private final List<String> ids;
    private final Map<String, Integer> indexById;
    private final BitSet exported;
    private int remaining;

    private CachedDecisions(final Record<DecisionEvaluationRecordValue> record) {
      partitionId = record.getPartitionId();
      position = record.getPosition();
      key = record.getKey();
      decisions = record.getValue().getEvaluatedDecisions();
      remaining = decisions.size();
      exported = new BitSet(remaining);
      indexById = HashMap.newHashMap(2 * remaining);

      final List<String> legacyIds = new ArrayList<>(remaining);
      final List<String> instanceKeys = new ArrayList<>(remaining);
      for (int i = 1; i <= remaining; i++) {
        final String legacyId = key + "-" + i;
        final String instanceKey = decisions.get(i - 1).getDecisionEvaluationInstanceKey();
        legacyIds.add(legacyId);
        instanceKeys.add(instanceKey);
        indexById.put(legacyId, i);
        if (!instanceKey.isEmpty()) {
          indexById.putIfAbsent(instanceKey, i);
        }
      }
      ids = instanceKeys.contains("") ? legacyIds : instanceKeys;
    }

    boolean isFor(final Record<DecisionEvaluationRecordValue> record) {
      return partitionId == record.getPartitionId()
          && position == record.getPosition()
          && key == record.getKey();
    }

    List<EvaluatedDecisionValue> decisions() {
      return decisions;
    }

    List<String> ids() {
      return ids;
    }

    /** Returns the 1-based index of the decision exported under the given entity id. */
    int indexOf(final String entityId) {
      final Integer index = indexById.get(entityId);
      if (index == null) {
        throw new IllegalStateException(
            "Decision evaluation %d has no evaluated decision with id %s (%d decisions)"
                .formatted(key, entityId, decisions.size()));
      }
      return index;
    }

    /** Marks the decision as exported; returns true once every decision of the record is. */
    boolean markExported(final int index) {
      if (!exported.get(index - 1)) {
        exported.set(index - 1);
        remaining--;
      }
      return remaining == 0;
    }
  }
}
