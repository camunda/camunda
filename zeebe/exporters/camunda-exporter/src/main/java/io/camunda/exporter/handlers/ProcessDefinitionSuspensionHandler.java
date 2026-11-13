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
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessDefinitionState;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import java.util.List;
import java.util.Map;

public class ProcessDefinitionSuspensionHandler
    implements MainIndexExporterHandler<ProcessEntity, ProcessDefinitionSuspensionRecordValue> {

  private final String indexName;

  public ProcessDefinitionSuspensionHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_DEFINITION_SUSPENSION;
  }

  @Override
  public Class<ProcessEntity> getEntityType() {
    return ProcessEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessDefinitionSuspensionRecordValue> record) {
    final var intent = record.getIntent();
    return intent == ProcessDefinitionSuspensionIntent.SUSPENDED
        || intent == ProcessDefinitionSuspensionIntent.RESUMED;
  }

  @Override
  public List<String> generateIds(final Record<ProcessDefinitionSuspensionRecordValue> record) {
    return record.getValue().getResolvedVersions().stream()
        .filter(version -> isStateChange(version.getOutcome()))
        .map(version -> String.valueOf(version.getProcessDefinitionKey()))
        .toList();
  }

  @Override
  public ProcessEntity createNewEntity(final String id) {
    return new ProcessEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessDefinitionSuspensionRecordValue> record, final ProcessEntity entity) {
    final ProcessDefinitionSuspensionResolvedVersionValue version =
        record.getValue().getResolvedVersions().stream()
            .filter(v -> String.valueOf(v.getProcessDefinitionKey()).equals(entity.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No resolved version with key "
                            + entity.getId()
                            + " in suspension record"));
    entity.setState(toState(version.getOutcome()));
  }

  @Override
  public void flush(
      final TargetIndex index, final ProcessEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.update(index, entity.getId(), Map.of(ProcessIndex.STATE, entity.getState()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private static boolean isStateChange(final ProcessDefinitionSuspensionOutcome outcome) {
    return outcome == ProcessDefinitionSuspensionOutcome.SUSPENDED
        || outcome == ProcessDefinitionSuspensionOutcome.RESUMED;
  }

  private static ProcessDefinitionState toState(final ProcessDefinitionSuspensionOutcome outcome) {
    return switch (outcome) {
      case SUSPENDED -> ProcessDefinitionState.SUSPENDED;
      case RESUMED -> ProcessDefinitionState.ACTIVE;
      case ALREADY_SUSPENDED, ALREADY_ACTIVE, DRAINING ->
          throw new IllegalArgumentException("Outcome " + outcome + " does not change the state");
    };
  }
}
