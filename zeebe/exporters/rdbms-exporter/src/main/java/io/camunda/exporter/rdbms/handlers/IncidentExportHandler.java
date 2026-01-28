/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.service.IncidentWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.TreePath;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentExportHandler implements RdbmsExportHandler<IncidentRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentExportHandler.class);

  private static final Set<Intent> INCIDENT_INTENTS =
      Set.of(IncidentIntent.CREATED, IncidentIntent.RESOLVED, IncidentIntent.MIGRATED);

  private final IncidentWriter incidentWriter;

  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public IncidentExportHandler(
      final IncidentWriter incidentWriter,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.incidentWriter = incidentWriter;
    this.processCache = processCache;
  }

  @Override
  public boolean canExport(final Record<IncidentRecordValue> record) {
    return record.getValueType() == ValueType.INCIDENT
        && INCIDENT_INTENTS.contains(record.getIntent());
  }

  @Override
  public void export(final Record<IncidentRecordValue> record) {
    if (record.getIntent().equals(IncidentIntent.CREATED)) {
      incidentWriter.create(map(record));
    } else if (record.getIntent().equals(IncidentIntent.RESOLVED)) {
      incidentWriter.resolve(record.getKey());
    } else if (record.getIntent().equals(IncidentIntent.MIGRATED)) {
      incidentWriter.update(map(record));
    } else {
      LOGGER.warn(
          "Unexpected incident intent {} for record {}/{}",
          record.getIntent(),
          record.getPartitionId(),
          record.getPosition());
    }
  }

  private IncidentDbModel map(final Record<IncidentRecordValue> record) {
    final var value = record.getValue();
    return new IncidentDbModel.Builder()
        .incidentKey(record.getKey())
        .flowNodeInstanceKey(value.getElementInstanceKey())
        .flowNodeId(value.getElementId())
        .processInstanceKey(mapIfGreaterZero(value.getProcessInstanceKey()))
        .rootProcessInstanceKey(mapIfGreaterZero(value.getRootProcessInstanceKey()))
        .processDefinitionKey(mapIfGreaterZero(value.getProcessDefinitionKey()))
        .processDefinitionId(value.getBpmnProcessId())
        .state(IncidentState.ACTIVE)
        .errorType(mapErrorType(value.getErrorType()))
        .errorMessage(value.getErrorMessage())
        .errorMessageHash(Optional.of(value.getErrorMessage()).map(String::hashCode).orElse(0))
        .creationDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .jobKey(mapIfGreaterZero(value.getJobKey()))
        .treePath(buildTreePath(record))
        .tenantId(value.getTenantId())
        .build();
  }

  private Long mapIfGreaterZero(final long key) {
    return key > 0 ? key : null;
  }

  private ErrorType mapErrorType(final io.camunda.zeebe.protocol.record.value.ErrorType errorType) {
    if (errorType == null) {
      return ErrorType.UNSPECIFIED;
    }
    try {
      return ErrorType.valueOf(errorType.name());
    } catch (final IllegalArgumentException ex) {
      return ErrorType.UNKNOWN;
    }
  }

  /*
   * This code was copied from zeebe/exporters/.../handlers/IncidentHandler.java
   * TODO: It should be refactored under https://github.com/camunda/camunda/issues/31218
   */
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
        // when we reached current processInstanceKey, we build the last piece of tree path
        treePath.appendFlowNode(value.getElementId());
        treePath.appendFlowNodeInstance(String.valueOf(value.getElementInstanceKey()));
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
