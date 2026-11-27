/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;

/**
 * Audits a probe when it is created and when it ends. The description is the probe ID; the input
 * and the answer data are never logged.
 */
public class ProbeAuditLogTransformer implements AuditLogTransformer<ProbeRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.PROBE_CONFIG;
  }

  @Override
  public Class<ProbeRecordValue> getRecordValueType() {
    return ProbeRecordValue.class;
  }

  @Override
  public void transform(final Record<ProbeRecordValue> record, final AuditLogEntry log) {
    final var value = record.getValue();
    log.setEntityDescription(value.getProbeId());
    if (isFailure(record)) {
      log.setResult(AuditLogOperationResult.FAIL);
    }
  }

  private static boolean isFailure(final Record<ProbeRecordValue> record) {
    final var intent = record.getIntent();
    if (intent == ProbeIntent.TIMED_OUT || intent == ProbeIntent.CANCELED) {
      return true;
    }
    return intent == ProbeIntent.ANSWERED && record.getValue().getOutcome() == ProbeOutcome.ERROR;
  }
}
