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
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
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
    log.setEntityDescription(value.getName());
  }

  @Override
  public boolean supports(final Record<VariableRecordValue> record) {
    final VariableRecordValue value = record.getValue();
    return VariableOperationType.API.equals(value.getSource().getType())
        && AuditLogTransformer.super.supports(record);
  }
}
