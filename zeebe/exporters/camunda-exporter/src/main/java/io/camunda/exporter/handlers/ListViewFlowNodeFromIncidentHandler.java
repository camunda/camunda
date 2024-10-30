/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.INCIDENT_POSITION;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operate.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewFlowNodeFromIncidentHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, IncidentRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewFlowNodeFromIncidentHandler.class);

  private final String indexName;
  private final boolean concurrencyMode;

  public ListViewFlowNodeFromIncidentHandler(
      final String indexName, final boolean concurrencyMode) {
    this.indexName = indexName;
    this.concurrencyMode = concurrencyMode;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<FlowNodeInstanceForListViewEntity> getEntityType() {
    return FlowNodeInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getElementInstanceKey()));
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(final String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<IncidentRecordValue> record, final FlowNodeInstanceForListViewEntity entity) {

    final String intentStr = record.getIntent().name();
    final IncidentRecordValue recordValue = record.getValue();

    // update activity instance
    entity
        .setId(String.valueOf(recordValue.getElementInstanceKey()))
        .setKey(recordValue.getElementInstanceKey())
        .setPartitionId(record.getPartitionId())
        .setPositionIncident(record.getPosition())
        .setActivityId(recordValue.getElementId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(trimWhitespace(recordValue.getErrorMessage()));
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setErrorMessage(null);
    }

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  @Override
  public void flush(
      final FlowNodeInstanceForListViewEntity entity, final BatchRequest batchRequest) {

    LOGGER.debug("Flow node instance for list view: id {}", entity.getId());
    final Map<String, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(ERROR_MSG, entity.getErrorMessage());
    updateFields.put(INCIDENT_POSITION, entity.getPositionIncident());

    final Long processInstanceKey = entity.getProcessInstanceKey();
    if (concurrencyMode) {
      batchRequest.upsertWithScriptAndRouting(
          indexName,
          entity.getId(),
          entity,
          getIncidentScript(),
          updateFields,
          String.valueOf(processInstanceKey));
    } else {
      batchRequest.upsertWithRouting(
          indexName, entity.getId(), entity, updateFields, String.valueOf(processInstanceKey));
    }
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  public String trimWhitespace(final String str) {
    return (str == null) ? null : str.strip();
  }

  protected String getIncidentScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "ctx._source.%s = params.%s; " // error message
            + "}",
        INCIDENT_POSITION,
        INCIDENT_POSITION,
        INCIDENT_POSITION,
        INCIDENT_POSITION,
        INCIDENT_POSITION,
        ERROR_MSG,
        ERROR_MSG);
  }
}
