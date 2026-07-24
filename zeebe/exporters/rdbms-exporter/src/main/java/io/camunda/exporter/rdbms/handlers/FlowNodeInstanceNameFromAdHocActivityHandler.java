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
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

/**
 * Names the synthetic ad-hoc sub-process "inner instance" element after its activated entry
 * element, so RDBMS reaches parity with the ES/OS exporter.
 *
 * <p>Activating an element inside an ad-hoc subprocess creates a synthetic <em>inner instance</em>:
 * a scope that owns the activated element together with everything reachable from it. That inner
 * instance has no name of its own, so this handler reacts to the entry child's {@code
 * ELEMENT_ACTIVATING} record and writes the child's resolved name onto the parent inner-instance
 * row (keyed by {@code flowScopeKey}), using set-if-null semantics so a later re-activation of a
 * different entry element cannot overwrite an already-resolved name.
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
    if (record.getIntent() != ProcessInstanceIntent.ELEMENT_ACTIVATING) {
      return false;
    }
    final var value = record.getValue();
    return processCache
        .get(value.getProcessDefinitionKey())
        .map(CachedProcessEntity::adHocActivityIds)
        .map(ids -> ids.contains(value.getElementId()))
        .orElse(false);
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    final var name =
        ProcessCacheUtil.getFlowNodeName(
                processCache, value.getProcessDefinitionKey(), value.getElementId())
            .orElse(value.getElementId());
    flowNodeInstanceWriter.updateName(value.getFlowScopeKey(), name);
  }
}
