/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent.MODIFIED;

import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import java.util.Set;

public class ProcessInstanceModificationAuditLogTransformer
    implements AuditLogOperationTransformer<
        ProcessInstanceModificationIntent, ProcessInstanceModificationRecordValue> {

  @Override
  public ValueType getValueType() {
    return PROCESS_INSTANCE_MODIFICATION;
  }

  @Override
  public Set<ProcessInstanceModificationIntent> getSupportedIntents() {
    return Set.of(MODIFIED);
  }

  @Override
  public Set<ProcessInstanceModificationIntent> getSupportedCommandRejections() {
    return Set.of();
  }

  @Override
  public Set<RejectionType> getSupportedRejectionTypes() {
    return Set.of();
  }

  @Override
  public void transform(
      final AuditLogEntity entity, final Record<ProcessInstanceModificationRecordValue> record) {
    final var value = record.getValue();
    entity
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setTenantId(value.getTenantId())
        .setTenantScope(AuditLogTenantScope.TENANT);
  }
}
