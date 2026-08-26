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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the error message of a process level incident on the process instance list view document.
 *
 * <p>Most incidents belong to a flow node, and {@link ListViewFlowNodeFromIncidentHandler} stores
 * their message on the corresponding flow node document. An incident raised at the process level
 * (e.g. by a process level execution listener) has no flow node to attach to, so its message is
 * stored on the process instance document itself, which is what searching process instances by
 * error message falls back to.
 */
public class ListViewProcessInstanceFromIncidentHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, IncidentRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewProcessInstanceFromIncidentHandler.class);

  private static final Set<IncidentIntent> SUPPORTED_INTENTS =
      EnumSet.of(IncidentIntent.CREATED, IncidentIntent.RESOLVED);

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
    return isProcessLevelIncident(record.getValue());
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

    // the process instance key is the entity key here; getProcessInstanceKey() reads it back
    entity
        .setId(String.valueOf(processInstanceKey))
        .setKey(processInstanceKey)
        .setPartitionId(record.getPartitionId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (record.getIntent() == IncidentIntent.CREATED) {
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
