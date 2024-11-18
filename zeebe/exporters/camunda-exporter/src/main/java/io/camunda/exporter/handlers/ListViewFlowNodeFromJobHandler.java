/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOB_FAILED_WITH_RETRIES_LEFT;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOB_POSITION;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operate.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewFlowNodeFromJobHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, JobRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewFlowNodeFromJobHandler.class);

  private static final Set<Intent> FAILED_JOB_EVENTS = Set.of(JobIntent.FAIL, JobIntent.FAILED);

  private final String indexName;
  private final boolean concurrencyMode;

  public ListViewFlowNodeFromJobHandler(final String indexName, final boolean concurrencyMode) {
    this.indexName = indexName;
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
  public boolean handlesRecord(final Record<JobRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(final Record<JobRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getElementInstanceKey()));
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(final String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<JobRecordValue> record, final FlowNodeInstanceForListViewEntity entity) {

    final var recordValue = record.getValue();
    final var intent = record.getIntent();

    entity
        .setId(String.valueOf(record.getValue().getElementInstanceKey()))
        .setKey(record.getValue().getElementInstanceKey())
        .setPartitionId(record.getPartitionId())
        .setPositionJob(record.getPosition())
        .setActivityId(recordValue.getElementId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .getJoinRelation()
        .setParent(recordValue.getProcessInstanceKey());

    if (FAILED_JOB_EVENTS.contains(intent) && recordValue.getRetries() > 0) {
      entity.setJobFailedWithRetriesLeft(true);
    } else {
      entity.setJobFailedWithRetriesLeft(false);
    }
  }

  @Override
  public void flush(
      final FlowNodeInstanceForListViewEntity entity, final BatchRequest batchRequest) {

    LOGGER.debug(
        "Update job state for flow node instance: id {} JobFailedWithRetriesLeft {}",
        entity.getId(),
        entity.isJobFailedWithRetriesLeft());
    final Map<String, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(JOB_FAILED_WITH_RETRIES_LEFT, entity.isJobFailedWithRetriesLeft());
    updateFields.put(JOB_POSITION, entity.getPositionJob());

    final Long processInstanceKey = entity.getProcessInstanceKey();
    if (concurrencyMode) {
      batchRequest.upsertWithScriptAndRouting(
          indexName,
          entity.getId(),
          entity,
          getFlowNodeInstanceFromJobScript(),
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

  protected String getFlowNodeInstanceFromJobScript() {
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
