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
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;

public class ProcessInstanceExportHandler
    implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  public static final long NO_PARENT_EXISTS_KEY = -1L;

  private final ProcessInstanceWriter processInstanceWriter;
  private final HistoryCleanupService historyCleanupService;

  public ProcessInstanceExportHandler(
      final ProcessInstanceWriter processInstanceWriter,
      final HistoryCleanupService historyCleanupService) {
    this.processInstanceWriter = processInstanceWriter;
    this.historyCleanupService = historyCleanupService;
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
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .state(ProcessInstanceState.ACTIVE)
        .startDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .tenantId(value.getTenantId())
        .parentProcessInstanceKey(parentProcessInstanceKey)
        .parentElementInstanceKey(parentElementInstanceKey)
        .version(value.getVersion())
        .partitionId(record.getPartitionId())
        .build();
  }
}
