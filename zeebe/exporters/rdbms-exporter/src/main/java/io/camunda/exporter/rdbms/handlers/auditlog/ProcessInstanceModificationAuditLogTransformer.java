/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import static io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerConfigs.PROCESS_INSTANCE_MODIFICATION_CONFIG;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;

public class ProcessInstanceModificationAuditLogTransformer
    implements AuditLogTransformer<
        ProcessInstanceModificationRecordValue, AuditLogDbModel.Builder> {

  @Override
  public TransformerConfig config() {
    return PROCESS_INSTANCE_MODIFICATION_CONFIG;
  }

  @Override
  public void transform(
      final Record<ProcessInstanceModificationRecordValue> record,
      final AuditLogDbModel.Builder entity) {
    final var value = record.getValue();
    entity
        .processInstanceKey(value.getProcessInstanceKey())
        .tenantId(value.getTenantId())
        .tenantScope(AuditLogTenantScope.TENANT);
  }
}
