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
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessInstanceDeletedHandler
    implements ExportHandler<FlowNodeInstanceEntity, ProcessInstanceRecordValue> {

  private final String indexName;
  private final Set<ProcessInstanceDependant> dependantIndices;
  private final ListViewTemplate listViewTemplate;

  public ProcessInstanceDeletedHandler(
      final String indexName, final IndexDescriptors indexDescriptors) {
    this.indexName = indexName;
    listViewTemplate = indexDescriptors.get(ListViewTemplate.class);
    dependantIndices =
        indexDescriptors.templates().stream()
            .filter(ProcessInstanceDependant.class::isInstance)
            .filter(t -> !(t instanceof OperationTemplate)) // Don't delete batch operations
            .map(ProcessInstanceDependant.class::cast)
            .collect(Collectors.toSet());
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<FlowNodeInstanceEntity> getEntityType() {
    return FlowNodeInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && ProcessInstanceIntent.DELETED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(final String id) {
    return new FlowNodeInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record, final FlowNodeInstanceEntity entity) {
    final ProcessInstanceRecordValue value = record.getValue();
    entity.setProcessInstanceKey(value.getProcessInstanceKey());
  }

  @Override
  public void flush(final FlowNodeInstanceEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    // POC 1: Add deletion to an index and delete async
    // batchRequest.add(indexName, entity);

    // POC 2: Directly delete from all indices
    dependantIndices.forEach(
        dependant -> {
          final var index = dependant.getFullQualifiedName();
          final var field = dependant.getProcessInstanceDependantField();
          batchRequest.deleteByField(index, field, entity.getId());
        });
    batchRequest.deleteByField(
        listViewTemplate.getFullQualifiedName(),
        ListViewTemplate.PROCESS_INSTANCE_KEY,
        entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
