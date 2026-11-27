/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.CaseIndex;
import io.camunda.webapps.schema.entities.cases.CaseEntity;
import io.camunda.webapps.schema.entities.cases.CaseStageEntity;
import io.camunda.webapps.schema.entities.cases.CaseStageState;
import io.camunda.webapps.schema.entities.cases.CaseState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports all case events into the case index. Records of the same batch share one entity, so
 * {@link #updateEntity} merges. The flush is an upsert with a script: when the document does not
 * exist yet (CREATED in this batch) the entity is stored as is, otherwise the script applies only
 * the partial changes so that a stage update never overwrites the stored stage graph.
 */
public class CaseHandler implements MainIndexExporterHandler<CaseEntity, CaseRecordValue> {

  private static final Set<CaseIntent> SUPPORTED_INTENTS =
      Set.of(
          CaseIntent.CREATED,
          CaseIntent.STAGE_UPDATED,
          CaseIntent.COMPLETED,
          CaseIntent.TERMINATED);

  // Applies the state and end date when set, then merges the stages by element id: a known stage
  // only gets its state updated, an unknown stage is appended as sent.
  private static final String SCRIPT =
      """
      if (params.state != null) {
        ctx._source.state = params.state;
      }
      if (params.endDate != null) {
        ctx._source.endDate = params.endDate;
      }
      if (params.stages != null) {
        if (ctx._source.stages == null) {
          ctx._source.stages = [];
        }
        for (def newStage : params.stages) {
          def found = false;
          for (def stage : ctx._source.stages) {
            if (stage.elementId == newStage.elementId) {
              stage.state = newStage.state;
              found = true;
              break;
            }
          }
          if (!found) {
            ctx._source.stages.add(newStage);
          }
        }
      }
      """;

  private final String indexName;

  public CaseHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.CASE;
  }

  @Override
  public Class<CaseEntity> getEntityType() {
    return CaseEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<CaseRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<CaseRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public CaseEntity createNewEntity(final String id) {
    return new CaseEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<CaseRecordValue> record, final CaseEntity entity) {
    final var value = record.getValue();

    switch ((CaseIntent) record.getIntent()) {
      case CREATED ->
          entity
              .setKey(value.getProcessInstanceKey())
              .setProcessInstanceKey(value.getProcessInstanceKey())
              .setProcessDefinitionKey(value.getProcessDefinitionKey())
              .setBpmnProcessId(value.getBpmnProcessId())
              .setTenantId(value.getTenantId())
              .setBusinessId(value.getBusinessId())
              .setTitle(value.getTitle())
              .setState(CaseState.ACTIVE)
              .setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
              .setStartElementIds(new ArrayList<>(value.getStartElementIds()))
              .setStages(new ArrayList<>(value.getStages().stream().map(this::toStage).toList()));
      case STAGE_UPDATED -> value.getStages().forEach(stage -> mergeStage(entity, stage));
      case COMPLETED ->
          entity
              .setState(CaseState.COMPLETED)
              .setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      case TERMINATED ->
          entity
              .setState(CaseState.TERMINATED)
              .setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      default -> {
        // not reachable, filtered by handlesRecord
      }
    }
  }

  @Override
  public void flush(
      final TargetIndex index, final CaseEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> params = new HashMap<>();
    params.put(CaseIndex.STATE, entity.getState() == null ? null : entity.getState().name());
    params.put(CaseIndex.END_DATE, entity.getEndDate());
    params.put(
        CaseIndex.STAGES,
        entity.getStages() == null
            ? null
            : entity.getStages().stream().map(this::toStageParams).toList());
    batchRequest.upsertWithScript(index, entity.getId(), entity, SCRIPT, params);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private CaseStageEntity toStage(final CaseStageValue stage) {
    return new CaseStageEntity()
        .setElementId(stage.getElementId())
        .setName(stage.getName())
        .setAlternative(stage.isAlternative())
        .setState(CaseStageState.valueOf(stage.getStatus().name()))
        .setNextElementIds(new ArrayList<>(stage.getNextElementIds()));
  }

  private void mergeStage(final CaseEntity entity, final CaseStageValue stage) {
    if (entity.getStages() == null) {
      entity.setStages(new ArrayList<>());
    }
    final var state = CaseStageState.valueOf(stage.getStatus().name());
    entity.getStages().stream()
        .filter(existing -> stage.getElementId().equals(existing.getElementId()))
        .findFirst()
        .ifPresentOrElse(
            existing -> existing.setState(state),
            () ->
                entity
                    .getStages()
                    .add(new CaseStageEntity().setElementId(stage.getElementId()).setState(state)));
  }

  private Map<String, Object> toStageParams(final CaseStageEntity stage) {
    final Map<String, Object> params = new HashMap<>();
    params.put("elementId", stage.getElementId());
    params.put("state", stage.getState().name());
    // the graph is only known when the stage came from CREATED, not from a bare STAGE_UPDATED
    if (stage.getNextElementIds() != null) {
      params.put("name", stage.getName());
      params.put("alternative", stage.isAlternative());
      params.put("nextElementIds", stage.getNextElementIds());
    }
    return params;
  }
}
