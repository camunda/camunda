/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOB_POSITION;
import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewFromJobHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, JobRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewFromJobHandler.class);

  private static final Set<String> FAILED_JOB_EVENTS =
      Set.of(JobIntent.FAIL.name(), JobIntent.FAILED.name());

  private final ListViewTemplate listViewTemplate;
  private final boolean concurrencyMode;

  public ListViewFromJobHandler(ListViewTemplate listViewTemplate, boolean concurrencyMode) {
    this.listViewTemplate = listViewTemplate;
    this.concurrencyMode = concurrencyMode;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.JOB;
  }

  @Override
  public Class<FlowNodeInstanceForListViewEntity> getEntityType() {
    return FlowNodeInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<JobRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(Record<JobRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getElementInstanceKey()));
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<JobRecordValue> record, FlowNodeInstanceForListViewEntity entity) {

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getValue().getElementInstanceKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getValue().getElementInstanceKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setPositionJob(record.getPosition());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));
    entity.getJoinRelation().setParent(recordValue.getProcessInstanceKey());

    if (FAILED_JOB_EVENTS.contains(intentStr) && recordValue.getRetries() > 0) {
      entity.setJobFailedWithRetriesLeft(true);
    } else {
      entity.setJobFailedWithRetriesLeft(false);
    }
  }

  @Override
  public void flush(
      FlowNodeInstanceForListViewEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {

    LOGGER.debug(
        "Update job state for flow node instance: id {} JobFailedWithRetriesLeft {}",
        entity.getId(),
        entity.isJobFailedWithRetriesLeft());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(JOB_FAILED_WITH_RETRIES_LEFT, entity.isJobFailedWithRetriesLeft());
    updateFields.put(JOB_POSITION, entity.getPositionJob());

    final Long processInstanceKey = entity.getProcessInstanceKey();
    try {
      if (concurrencyMode) {
        batchRequest.upsertWithScriptAndRouting(
            getIndexName(),
            entity.getId(),
            entity,
            getFlowNodeInstanceFromJobScript(),
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

  private String getFlowNodeInstanceFromJobScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "ctx._source.%s = params.%s; " // failed with retries
            + "}",
        JOB_POSITION,
        JOB_POSITION,
        JOB_POSITION,
        JOB_POSITION,
        JOB_POSITION,
        JOB_FAILED_WITH_RETRIES_LEFT,
        JOB_FAILED_WITH_RETRIES_LEFT);
  }
}
