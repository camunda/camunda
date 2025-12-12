/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import static io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerConfigs.BATCH_OPERATION_CREATION_CONFIG;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel.Builder;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.util.EnumUtil;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;

public class BatchOperationCreationAuditLogTransformer
    implements AuditLogTransformer<BatchOperationCreationRecordValue, Builder> {

  @Override
  public TransformerConfig config() {
    return BATCH_OPERATION_CREATION_CONFIG;
  }

  @Override
  public void transform(
      final Record<BatchOperationCreationRecordValue> record, final Builder entity) {
    final var value = record.getValue();
    entity
        .batchOperationType(
            EnumUtil.convert(value.getBatchOperationType(), BatchOperationType.class))
        .tenantScope(AuditLogTenantScope.GLOBAL);
  }
}
