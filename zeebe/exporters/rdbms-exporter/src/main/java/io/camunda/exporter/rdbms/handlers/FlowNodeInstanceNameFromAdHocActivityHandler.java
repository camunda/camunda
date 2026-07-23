/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

/**
 * Names the synthetic ad-hoc sub-process "inner instance" element after its activated entry
 * element, so RDBMS reaches parity with the ES/OS exporter.
 *
 * <p>This is a no-op stub introduced together with the red TDD tests; the real behavior is added in
 * the subsequent green step.
 */
public class FlowNodeInstanceNameFromAdHocActivityHandler
    implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public FlowNodeInstanceNameFromAdHocActivityHandler(
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
    this.processCache = processCache;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecordValue> record) {
    return false;
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    // no-op stub; real behavior added in the green step
  }
}
