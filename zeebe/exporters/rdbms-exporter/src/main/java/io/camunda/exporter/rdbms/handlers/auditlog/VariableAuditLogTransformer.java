/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel.Builder;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerConfigs;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

public class VariableAuditLogTransformer
    implements AuditLogTransformer<VariableRecordValue, AuditLogDbModel.Builder> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.VARIABLE_CONFIG;
  }

  @Override
  public void transform(final Record<VariableRecordValue> record, final Builder entity) {
    final var value = record.getValue();
    entity
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .elementInstanceKey(value.getScopeKey())
        .tenantId(value.getTenantId())
        .tenantScope(AuditLogEntity.AuditLogTenantScope.TENANT);
  }
}
