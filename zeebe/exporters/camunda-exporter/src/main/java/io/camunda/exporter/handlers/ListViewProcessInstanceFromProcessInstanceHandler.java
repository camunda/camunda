/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.*;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewProcessInstanceFromProcessInstanceHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, ProcessInstanceRecordValue> {

  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewProcessInstanceFromProcessInstanceHandler.class);

  private static final Set<String> PI_AND_AI_START_STATES = Set.of(ELEMENT_ACTIVATING.name());
  private static final Set<String> PI_AND_AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());

  private final boolean concurrencyMode;
  private final String indexName;

  public ListViewProcessInstanceFromProcessInstanceHandler(
      final String indexName, final boolean concurrencyMode) {
    this.indexName = indexName;
    this.concurrencyMode = concurrencyMode;
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
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    final var recordValue = record.getValue();
    if (isProcessEvent(recordValue)) {
      final var intent = record.getIntent().name();
      return PI_AND_AI_START_STATES.contains(intent)
          || PI_AND_AI_FINISH_STATES.contains(intent)
          || ELEMENT_MIGRATED.name().equals(intent);
    }
    return false;
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record,
      final ProcessInstanceForListViewEntity piEntity) {

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    piEntity
        .setId(String.valueOf(recordValue.getProcessInstanceKey()))
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setProcessVersion(recordValue.getVersion())
        .setProcessName(
            getProcessName(piEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()));

    final OffsetDateTime timestamp =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC);
    final boolean isRootProcessInstance =
        recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      incrementFinishedCount();
      piEntity.setEndDate(timestamp);
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      piEntity.setStartDate(timestamp).setState(ProcessInstanceState.ACTIVE);
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    // call activity related fields
    if (!isRootProcessInstance) {
      piEntity
          .setParentProcessInstanceKey(recordValue.getParentProcessInstanceKey())
          .setParentFlowNodeInstanceKey(recordValue.getParentElementInstanceKey());
    }
    completeOperation(record);
  }

  @Override
  public void flush(
      final ProcessInstanceForListViewEntity entity, final BatchRequest batchRequest) {

    final Map<String, Object> updateFields = new HashMap<>();
    if (entity.getStartDate() != null) {
      updateFields.put(ListViewTemplate.START_DATE, entity.getStartDate());
    }
    if (entity.getEndDate() != null) {
      updateFields.put(ListViewTemplate.END_DATE, entity.getEndDate());
    }
    updateFields.put(ListViewTemplate.PROCESS_NAME, entity.getProcessName());
    updateFields.put(ListViewTemplate.PROCESS_VERSION, entity.getProcessVersion());
    updateFields.put(ListViewTemplate.PROCESS_KEY, entity.getProcessDefinitionKey());
    updateFields.put(ListViewTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    updateFields.put(POSITION, entity.getPosition());
    if (entity.getState() != null) {
      updateFields.put(ListViewTemplate.STATE, entity.getState());
    }

    if (concurrencyMode) {
      batchRequest.upsertWithScriptAndRouting(
          indexName,
          entity.getId(),
          entity,
          getProcessInstanceScript(),
          updateFields,
          String.valueOf(entity.getProcessInstanceKey()));
    } else {
      batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
    }
  }

  private boolean isProcessEvent(final ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(
      final ProcessInstanceRecordValue recordValue, final BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

  private boolean isProcessInstanceTerminated(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() == ELEMENT_TERMINATED;
  }

  private boolean isProcessInstanceMigrated(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() == ELEMENT_MIGRATED;
  }

  protected String getProcessInstanceScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "ctx._source.%s = params.%s; " // process name
            + "ctx._source.%s = params.%s; " // process version
            + "ctx._source.%s = params.%s; " // process key
            + "ctx._source.%s = params.%s; " // bpmnProcessId
            + "if (params.%s != null) { ctx._source.%s = params.%s; }" // start date
            + "if (params.%s != null) { ctx._source.%s = params.%s; }" // end date
            + "if (params.%s != null) { ctx._source.%s = params.%s; }" // state
            + "}",
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        PROCESS_NAME,
        PROCESS_NAME,
        PROCESS_VERSION,
        PROCESS_VERSION,
        PROCESS_KEY,
        PROCESS_KEY,
        BPMN_PROCESS_ID,
        BPMN_PROCESS_ID,
        START_DATE,
        START_DATE,
        START_DATE,
        END_DATE,
        END_DATE,
        END_DATE,
        STATE,
        STATE,
        STATE);
  }

  /// TODO - because it depends on importBatch
  private void incrementFinishedCount() {
    /*
    final ImportBatch importBatch = null;
    if (importBatch == null) {
      return;
    }

    importBatch.incrementFinishedWiCount();
    */
  }

  /// TODO - because it depends on operationsManager and batchRequest
  private void completeOperation(final Record<ProcessInstanceRecordValue> record) {
    // TODO: Implement Operations manager for Exporter
    // https://github.com/camunda/camunda/issues/18372
    //
    //    final OperationsManager operationsManager = null;
    //    final NewElasticsearchBatchRequest batchRequest = null;
    //    if (operationsManager == null || batchRequest == null) {
    //      return;
    //    }
    //
    //    try {
    //      if (isProcessInstanceTerminated(record)) {
    //        // resolve corresponding operation
    //        operationsManager.completeOperation(
    //            null, record.getKey(), null, OperationType.CANCEL_PROCESS_INSTANCE, batchRequest);
    //      } else if (isProcessInstanceMigrated(record)) {
    //        // resolve corresponding operation
    //        operationsManager.completeOperation(
    //            null, record.getKey(), null, OperationType.MIGRATE_PROCESS_INSTANCE,
    // batchRequest);
    //      }
    //    } catch (final PersistenceException ex) {
    //      throw new OperateRuntimeException(ex);
    //    }
  }

  /// TODO - because it depends on processCache
  private String getProcessName(final Long processDefinitionKey, final String bpmnProcessId) {
    return bpmnProcessId;

    // TODO https://github.com/camunda/camunda/issues/18364
    //    final ProcessCache processCache = null;
    //    if (processCache == null) {
    //      return bpmnProcessId;
    //    }
    //
    //    return processCache.getProcessNameOrDefaultValue(processDefinitionKey, bpmnProcessId);
  }
}
