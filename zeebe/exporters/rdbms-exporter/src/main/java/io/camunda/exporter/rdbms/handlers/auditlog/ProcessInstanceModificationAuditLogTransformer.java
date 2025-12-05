/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.exporter.common.handlers.auditlog.AbstractProcessInstanceModificationAuditLogTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;

public class ProcessInstanceModificationAuditLogTransformer
    extends AbstractProcessInstanceModificationAuditLogTransformer<AuditLogDbModel.Builder> {

  @Override
  public void transform(
      final AuditLogDbModel.Builder entity,
      final Record<ProcessInstanceModificationRecordValue> record) {
    final var value = record.getValue();
    entity
        .processInstanceKey(value.getProcessInstanceKey())
        .tenantId(value.getTenantId())
        .tenantScope(AuditLogTenantScope.TENANT);
  }
}
