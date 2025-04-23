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
import io.camunda.exporter.rdbms.cache.CachedProcessEntity;
import io.camunda.exporter.rdbms.cache.ExporterEntityCache;
import io.camunda.exporter.rdbms.utils.ProcessCacheUtil;
import io.camunda.exporter.rdbms.utils.TreePath;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
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
    return new ProcessInstanceDbModelBuilder()
        .processInstanceKey(value.getProcessInstanceKey())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .state(ProcessInstanceState.ACTIVE)
        .startDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .tenantId(value.getTenantId())
        .parentProcessInstanceKey(value.getParentProcessInstanceKey())
        .parentElementInstanceKey(value.getParentElementInstanceKey())
        .version(value.getVersion())
        .partitionId(record.getPartitionId())
        .treePath(createTreePath(record).toString())
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

    final TreePath treePath = new TreePath();
    for (int i = 0; i < elementInstancePath.size(); i++) {
      final List<Long> keysWithinOnePI = elementInstancePath.get(i);
      treePath.appendProcessInstance(keysWithinOnePI.getFirst());
      if (keysWithinOnePI.getFirst().equals(processInstanceKey)) {
        // we reached the leaf of the tree path, when we reached current processInstanceKey
        break;
      }
      final var callActivity =
          ProcessCacheUtil.getCallActivityId(
              processCache, processDefinitionPath.get(i), callingElementPath.get(i));
      if (callActivity.isPresent()) {
        treePath.appendFlowNode(callActivity.get());
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
