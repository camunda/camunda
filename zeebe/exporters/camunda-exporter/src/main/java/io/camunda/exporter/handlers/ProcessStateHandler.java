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
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.List;
import java.util.Map;

public class ProcessStateHandler implements MainIndexExporterHandler<ProcessEntity, Process> {

  private static final Map<Intent, ProcessDefinitionState> STATE_BY_INTENT =
      Map.of(
          ProcessIntent.DRAINING, ProcessDefinitionState.DRAINING,
          ProcessIntent.SUSPENDED, ProcessDefinitionState.SUSPENDED,
          ProcessIntent.RESUMED, ProcessDefinitionState.ACTIVE);

  private final String indexName;

  public ProcessStateHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS;
  }

  @Override
  public Class<ProcessEntity> getEntityType() {
    return ProcessEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<Process> record) {
    return STATE_BY_INTENT.containsKey(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<Process> record) {
    return List.of(String.valueOf(record.getValue().getProcessDefinitionKey()));
  }

  @Override
  public ProcessEntity createNewEntity(final String id) {
    return new ProcessEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<Process> record, final ProcessEntity entity) {
    entity.setState(STATE_BY_INTENT.get(record.getIntent()));
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
}
