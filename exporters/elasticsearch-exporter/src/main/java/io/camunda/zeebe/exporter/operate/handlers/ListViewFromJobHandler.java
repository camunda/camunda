/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewFromJobHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, JobRecordValue> {

  // TODO: has the same problem like other handlers in that it updates an entity actually created by
  // another handler

  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewFromJobHandler.class);

  private static final Set<String> FAILED_JOB_EVENTS = new HashSet<>();

  static {
    FAILED_JOB_EVENTS.add(JobIntent.FAIL.name());
    FAILED_JOB_EVENTS.add(JobIntent.FAILED.name());
  }

  private ListViewTemplate listViewTemplate;

  public ListViewFromJobHandler(ListViewTemplate listViewTemplate) {
    this.listViewTemplate = listViewTemplate;
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
  public String generateId(Record<JobRecordValue> record) {
    return ConversionUtils.toStringOrNull(record.getValue().getElementInstanceKey());
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
    entity.setPartitionId(record.getPartitionId());
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
  public void flush(FlowNodeInstanceForListViewEntity entity, BatchRequest batchRequest)
      throws PersistenceException {

    LOGGER.debug(
        "Update job state for flow node instance: id {} JobFailedWithRetriesLeft {}",
        entity.getId(),
        entity.isJobFailedWithRetriesLeft());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ListViewTemplate.ID, entity.getId());
    updateFields.put(
        ListViewTemplate.JOB_FAILED_WITH_RETRIES_LEFT, entity.isJobFailedWithRetriesLeft());

    batchRequest.upsertWithRouting(
        listViewTemplate.getFullQualifiedName(),
        entity.getId(),
        entity,
        updateFields,
        String.valueOf(entity.getProcessInstanceKey()));
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }
}
