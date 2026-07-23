/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessDefinitionState;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.List;
import java.util.Map;

public class ProcessDeletedHandler implements ExportHandler<ProcessEntity, Process> {

  private final String indexName;

  public ProcessDeletedHandler(final String indexName) {
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
    return record.getIntent() == ProcessIntent.DELETED;
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
    entity.setState(ProcessDefinitionState.DELETED);
  }

  @Override
  public void flush(final ProcessEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.update(
        indexName, entity.getId(), Map.of(ProcessIndex.STATE, ProcessDefinitionState.DELETED));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
