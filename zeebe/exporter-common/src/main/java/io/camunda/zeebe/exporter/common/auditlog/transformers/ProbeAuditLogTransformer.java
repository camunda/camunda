/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;

/**
 * Audits a probe when it is created and when it ends, as {@link JobAuditLogTransformer} does for
 * jobs. The description is the probe ID; the input is never logged.
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
    log.setEntityKey(String.valueOf(record.getKey()));
    log.setEntityDescription(value.getProbeId());
  }
}
