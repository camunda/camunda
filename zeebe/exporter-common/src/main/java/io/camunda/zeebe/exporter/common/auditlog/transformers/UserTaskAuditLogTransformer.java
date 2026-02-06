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
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;

public class UserTaskAuditLogTransformer implements AuditLogTransformer<UserTaskRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.USER_TASK_CONFIG;
  }

  @Override
  public void transform(final Record<UserTaskRecordValue> record, final AuditLogEntry log) {
    final var value = record.getValue();
    log.setUserTaskKey(value.getUserTaskKey()).setEntityKey(String.valueOf(value.getUserTaskKey()));
  }
}
