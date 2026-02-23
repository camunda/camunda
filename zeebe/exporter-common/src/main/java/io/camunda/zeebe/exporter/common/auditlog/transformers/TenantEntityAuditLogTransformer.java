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
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;

public class TenantEntityAuditLogTransformer implements AuditLogTransformer<TenantRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.TENANT_ENTITY_CONFIG;
  }

  @Override
  public void transform(final Record<TenantRecordValue> record, final AuditLogEntry log) {
    final TenantRecordValue value = record.getValue();
    log.setEntityKey(value.getTenantId())
        .setRelatedEntityKey(value.getEntityId())
        .setRelatedEntityType(
            AuditLogTransformerConfigs.mapEntityTypeToAuditLogEntityType(value.getEntityType()));
  }
}
