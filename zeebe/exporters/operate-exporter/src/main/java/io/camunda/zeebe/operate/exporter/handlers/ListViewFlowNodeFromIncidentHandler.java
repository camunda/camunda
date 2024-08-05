/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.operate.schema.templates.ListViewTemplate.ERROR_MSG;
import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT_POSITION;
import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class ListViewFlowNodeFromIncidentHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, IncidentRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewFlowNodeFromIncidentHandler.class);

  private final ListViewTemplate listViewTemplate;
  private final boolean concurrencyMode;

  public ListViewFlowNodeFromIncidentHandler(
      ListViewTemplate listViewTemplate, boolean concurrencyMode) {
    this.listViewTemplate = listViewTemplate;
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
  public boolean handlesRecord(Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(Record<IncidentRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getElementInstanceKey()));
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<IncidentRecordValue> record, FlowNodeInstanceForListViewEntity entity) {

    final String intentStr = record.getIntent().name();
    final IncidentRecordValue recordValue = record.getValue();

    // update activity instance
    entity
        .setId(ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()))
        .setKey(recordValue.getElementInstanceKey())
        .setPartitionId(record.getPartitionId())
        .setPositionIncident(record.getPosition())
        .setActivityId(recordValue.getElementId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setErrorMessage(null);
    }

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  @Override
  public void flush(
      FlowNodeInstanceForListViewEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {

    LOGGER.debug("Flow node instance for list view: id {}", entity.getId());
    final Map<String, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(ERROR_MSG, entity.getErrorMessage());
    updateFields.put(INCIDENT_POSITION, entity.getPositionIncident());

    final Long processInstanceKey = entity.getProcessInstanceKey();
    try {
      if (concurrencyMode) {
        batchRequest.upsertWithScriptAndRouting(
            getIndexName(),
            entity.getId(),
            entity,
            getIncidentScript(),
            updateFields,
            String.valueOf(processInstanceKey));
      } else {
        batchRequest.upsertWithRouting(
            getIndexName(),
            entity.getId(),
            entity,
            updateFields,
            String.valueOf(processInstanceKey));
      }
    } catch (PersistenceException ex) {
      final String error =
          String.format(
              "Error while upserting entity of type %s with id %s",
              entity.getClass().getSimpleName(), entity.getId());
      LOGGER.error(error, ex);
      throw new OperateRuntimeException(error, ex);
    }
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }

  private String getIncidentScript() {
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
