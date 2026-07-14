/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ERROR_MSG;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incidents raised directly on a process instance (no specific element/flow node, e.g. a
 * failed execution listener on the process start event) by writing the error message onto the
 * process instance's own list-view document, since {@link ListViewFlowNodeFromIncidentHandler}
 * explicitly skips these (there is no flow node/activity document to attach the error message to).
 */
public class ListViewProcessInstanceFromIncidentHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, IncidentRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewProcessInstanceFromIncidentHandler.class);

  private static final Set<IncidentIntent> SUPPORTED_INTENTS =
      Set.of(IncidentIntent.CREATED, IncidentIntent.RESOLVED);

  private final String indexName;

  public ListViewProcessInstanceFromIncidentHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    if (!SUPPORTED_INTENTS.contains((IncidentIntent) record.getIntent())) {
      return false;
    }
    final var recordValue = record.getValue();
    return isProcessLevelIncident(recordValue);
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<IncidentRecordValue> record, final ProcessInstanceForListViewEntity entity) {

    final IncidentRecordValue recordValue = record.getValue();
    final long processInstanceKey = recordValue.getProcessInstanceKey();

    entity
        .setId(String.valueOf(processInstanceKey))
        .setKey(processInstanceKey)
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (record.getIntent().equals(IncidentIntent.CREATED)) {
      entity.setErrorMessage(trimWhitespace(recordValue.getErrorMessage()));
    } else {
      entity.setErrorMessage(null);
    }
  }

  @Override
  public void flush(
      final ProcessInstanceForListViewEntity entity, final BatchRequest batchRequest) {

    LOGGER.debug("Process instance for list view: id {}", entity.getId());
    final Map<String, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(ERROR_MSG, entity.getErrorMessage());

    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private String trimWhitespace(final String str) {
    return (str == null) ? null : str.strip();
  }

  private boolean isProcessLevelIncident(final IncidentRecordValue recordValue) {
    return recordValue.getProcessInstanceKey() == recordValue.getElementInstanceKey();
  }
}
