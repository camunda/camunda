/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.*;

import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.notifier.IncidentNotifier;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.exporter.utils.ProcessCacheUtil;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentHandler implements ExportHandler<IncidentEntity, IncidentRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentHandler.class);
  private final String indexName;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private final IncidentNotifier incidentNotifier;

  public IncidentHandler(
      final String indexName,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final IncidentNotifier incidentNotifier) {
    this.indexName = indexName;
    this.processCache = processCache;
    this.incidentNotifier = incidentNotifier;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<IncidentEntity> getEntityType() {
    return IncidentEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    final var intent = record.getIntent();
    return !intent.equals(IncidentIntent.RESOLVED);
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    return List.of(ExporterUtil.toStringOrNull(record.getKey()));
  }

  @Override
  public IncidentEntity createNewEntity(final String id) {
    return new IncidentEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<IncidentRecordValue> record, final IncidentEntity entity) {
    final IncidentRecordValue recordValue = record.getValue();
    final long incidentKey = record.getKey();
    entity
        .setId(ExporterUtil.toStringOrNull(incidentKey))
        .setKey(incidentKey)
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition());
    if (recordValue.getJobKey() > 0) {
      entity.setJobKey(recordValue.getJobKey());
    }
    if (recordValue.getProcessInstanceKey() > 0) {
      entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    }
    if (recordValue.getProcessDefinitionKey() > 0) {
      entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    }
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    final String errorMessage = ExporterUtil.trimWhitespace(recordValue.getErrorMessage());
    entity
        .setErrorMessage(errorMessage)
        .setErrorType(
            ErrorType.fromZeebeErrorType(
                recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()))
        .setFlowNodeId(recordValue.getElementId());
    if (recordValue.getElementInstanceKey() > 0) {
      entity.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
    }
    entity
        .setState(IncidentState.PENDING)
        .setCreationTime(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC))
        .setTenantId(ExporterUtil.tenantOrDefault(recordValue.getTenantId()));

    entity.setTreePath(buildTreePath(record));

    final Intent intent = (record == null) ? null : record.getIntent();
    if (intent == null) {
      LOGGER.warn("Intent is null for incident: id {}", entity.getId());
    }
    if (Objects.equals(intent, IncidentIntent.CREATED)) {
      incidentNotifier.notifyAsync(List.of(entity));
    }
  }

  @Override
  public void flush(final IncidentEntity entity, final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(BPMN_PROCESS_ID, entity.getBpmnProcessId());
    updateFields.put(PROCESS_DEFINITION_KEY, entity.getProcessDefinitionKey());
    updateFields.put(FLOW_NODE_ID, entity.getFlowNodeId());
    updateFields.put(POSITION, entity.getPosition());
    updateFields.put(TREE_PATH, entity.getTreePath());
    batchRequest.upsert(indexName, String.valueOf(entity.getKey()), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private String buildTreePath(final Record<IncidentRecordValue> record) {

    final IncidentRecordValue value = record.getValue();
    final List<List<Long>> elementInstancePath = value.getElementInstancePath();
    final List<Integer> callingElementPath = value.getCallingElementPath();
    final List<Long> processDefinitionPath = value.getProcessDefinitionPath();

    final Long processInstanceKey = value.getProcessInstanceKey();

    // example of how the tree path is built when current instance is on the third level of calling
    // hierarchy:
    // PI_<parentProcessInstanceKey>/FN_<parentCallActivityId>/FNI_<parentCallActivityInstanceKey>/
    // PI_<secondLevelProcessInstanceKey>/FN_<secondLevelCallActivityId>/FNI_<secondLevelCallActivityInstanceKey>/
    // PI_<currentProcessInstanceKey>/FN_<flowNodeId>/FNI_<flowNodeInstanceId>
    final TreePath treePath = new TreePath();
    for (int i = 0; i < elementInstancePath.size(); i++) {
      final List<Long> keysWithinOnePI = elementInstancePath.get(i);
      treePath.appendProcessInstance(keysWithinOnePI.get(0));
      if (keysWithinOnePI.get(0).equals(processInstanceKey)) {
        // when we reached current processInstanceKey, we build the last peace of tree path
        treePath
            .appendFlowNode(value.getElementId())
            .appendFlowNodeInstance(value.getElementInstanceKey());
        break;
      } else {
        final var callActivityId =
            ProcessCacheUtil.getCallActivityId(
                processCache, processDefinitionPath.get(i), callingElementPath.get(i));
        if (callActivityId.isPresent()) {
          treePath.appendFlowNode(callActivityId.get());
        } else {
          LOGGER.warn(
              "No process found in cache. TreePath won't contain proper callActivityId. processInstanceKey: {}, processDefinitionKey: {}, incidentKey: {}",
              processInstanceKey,
              processDefinitionPath.get(i),
              record.getKey());
          treePath.appendFlowNode(String.valueOf(callingElementPath.get(i)));
        }
        treePath.appendFlowNodeInstance(String.valueOf(keysWithinOnePI.get(1)));
      }
    }

    return treePath.toString();
  }
}
