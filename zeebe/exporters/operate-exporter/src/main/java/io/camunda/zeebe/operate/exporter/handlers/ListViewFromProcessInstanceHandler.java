/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewFromProcessInstanceHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, ProcessInstanceRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewFromProcessInstanceHandler.class);

  private static final Set<String> PI_AND_AI_START_STATES = Set.of(ELEMENT_ACTIVATING.name());
  private static final Set<String> PI_AND_AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());

  private final ListViewTemplate listViewTemplate;

  public ListViewFromProcessInstanceHandler(ListViewTemplate listViewTemplate) {
    this.listViewTemplate = listViewTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent().name();
    return PI_AND_AI_START_STATES.contains(intent)
        || PI_AND_AI_FINISH_STATES.contains(intent)
        || ELEMENT_MIGRATED.name().equals(intent);
  }

  @Override
  public List<String> generateIds(Record<ProcessInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<ProcessInstanceRecordValue> record, ProcessInstanceForListViewEntity entity) {

    // FIX Implement
    /*
     handles multiple records at once
     has additional parameter ImportBatch
     updated multiple types of entities (ProcessInstance and FlowNode)
     updates operations via OperationManager
    */
  }

  @Override
  public void flush(
      ProcessInstanceForListViewEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {

    // FIX Implement
    /*
     handles multiple records at once
     has additional parameter ImportBatch
     updated multiple types of entities (ProcessInstance and FlowNode)
     updates operations via OperationManager
    */
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }
}
