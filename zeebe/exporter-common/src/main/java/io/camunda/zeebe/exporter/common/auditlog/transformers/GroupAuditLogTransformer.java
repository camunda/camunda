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
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;

public class GroupAuditLogTransformer implements AuditLogTransformer<GroupRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.GROUP_CONFIG;
  }

  @Override
  public void transform(final Record<GroupRecordValue> record, final AuditLogEntry log) {
    final GroupRecordValue value = record.getValue();
    log.setEntityKey(value.getGroupId()).setEntityDescription(value.getName());
  }
}
