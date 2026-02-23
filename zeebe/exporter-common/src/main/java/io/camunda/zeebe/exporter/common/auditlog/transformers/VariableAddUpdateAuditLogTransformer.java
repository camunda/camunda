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
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

public class VariableAddUpdateAuditLogTransformer
    implements AuditLogTransformer<VariableRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.VARIABLE_ADD_UPDATE_CONFIG;
  }

  @Override
  public void transform(final Record<VariableRecordValue> record, final AuditLogEntry log) {
    final VariableRecordValue value = record.getValue();
    final long rootProcessInstanceKey = value.getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      log.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
    log.setEntityDescription(value.getName());
  }
}
