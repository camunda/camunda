/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.TreePath;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceExportHandler
    implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  public static final long NO_PARENT_EXISTS_KEY = -1L;
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstanceExportHandler.class);

  private final ProcessInstanceWriter processInstanceWriter;
  private final HistoryCleanupService historyCleanupService;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public ProcessInstanceExportHandler(
      final ProcessInstanceWriter processInstanceWriter,
      final HistoryCleanupService historyCleanupService,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.processInstanceWriter = processInstanceWriter;
    this.historyCleanupService = historyCleanupService;
    this.processCache = processCache;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecordValue> record) {
    return record.getValueType() == ValueType.PROCESS_INSTANCE
        && record.getValue().getBpmnElementType() == BpmnElementType.PROCESS;
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING)) {
      processInstanceWriter.create(map(record));
    } else if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED)) {
      final OffsetDateTime endDate = DateUtil.toOffsetDateTime(record.getTimestamp());
      processInstanceWriter.finish(
          value.getProcessInstanceKey(), ProcessInstanceState.COMPLETED, endDate);
      historyCleanupService.scheduleProcessForHistoryCleanup(
          value.getProcessInstanceKey(), endDate);
    } else if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED)) {
      final OffsetDateTime endDate = DateUtil.toOffsetDateTime(record.getTimestamp());
      processInstanceWriter.finish(
          value.getProcessInstanceKey(), ProcessInstanceState.CANCELED, endDate);
      historyCleanupService.scheduleProcessForHistoryCleanup(
          value.getProcessInstanceKey(), endDate);
    } else if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_MIGRATED)) {
      processInstanceWriter.update(map(record));
    }
  }

  private ProcessInstanceDbModel map(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();

    // To be consistent with the other exporters, we use null for the parent keys if there is no
    // parent
    final var parentProcessInstanceKey =
        value.getParentProcessInstanceKey() == NO_PARENT_EXISTS_KEY
            ? null
            : value.getParentProcessInstanceKey();
    final var parentElementInstanceKey =
        value.getParentElementInstanceKey() == NO_PARENT_EXISTS_KEY
            ? null
            : value.getParentElementInstanceKey();

    return new ProcessInstanceDbModelBuilder()
        .processInstanceKey(value.getProcessInstanceKey())
        .rootProcessInstanceKey(value.getRootProcessInstanceKey())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .state(ProcessInstanceState.ACTIVE)
        .startDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .tenantId(value.getTenantId())
        .parentProcessInstanceKey(parentProcessInstanceKey)
        .parentElementInstanceKey(parentElementInstanceKey)
        .version(value.getVersion())
        .partitionId(record.getPartitionId())
        .treePath(createTreePath(record).toString())
        .tags(value.getTags())
        .build();
  }

  public TreePath createTreePath(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    final var elementInstancePath = value.getElementInstancePath();
    final var processDefinitionPath = value.getProcessDefinitionPath();
    final var callingElementPath = value.getCallingElementPath();
    final Long processInstanceKey = value.getProcessInstanceKey();
    if (elementInstancePath == null || elementInstancePath.isEmpty()) {
      return new TreePath().startTreePath(processInstanceKey);
    }

    final var callActivities =
        ProcessCacheUtil.getCallActivityIds(processCache, processDefinitionPath);

    final TreePath treePath = new TreePath();
    for (int i = 0; i < elementInstancePath.size(); i++) {
      final List<Long> keysWithinOnePI = elementInstancePath.get(i);
      treePath.appendProcessInstance(keysWithinOnePI.getFirst());
      if (keysWithinOnePI.getFirst().equals(processInstanceKey)) {
        // we reached the leaf of the tree path, when we reached current processInstanceKey
        break;
      }
      final var callActivity = callActivities.get(i);
      if (callActivity != null && !callActivity.isEmpty()) {
        treePath.appendFlowNode(callActivity.get(callingElementPath.get(i)));
      } else {
        final var index = callingElementPath.get(i);
        LOGGER.warn(
            "Expected to find process in cache. TreePath won't contain proper callActivityId, will use the lexicographic index instead {}. [processInstanceKey: {}, processDefinitionKey: {}, incidentKey: {}]",
            processInstanceKey,
            processDefinitionPath.get(i),
            record.getKey(),
            index);

        treePath.appendFlowNode(String.valueOf(callingElementPath.get(i)));
      }
      treePath.appendFlowNodeInstance(String.valueOf(keysWithinOnePI.getLast()));
    }
    return treePath;
  }
}
