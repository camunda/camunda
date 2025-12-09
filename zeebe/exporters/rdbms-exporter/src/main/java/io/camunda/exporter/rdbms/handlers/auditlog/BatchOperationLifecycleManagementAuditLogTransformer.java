/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel.Builder;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerConfigs;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;

public class BatchOperationLifecycleManagementAuditLogTransformer
    implements AuditLogTransformer<BatchOperationLifecycleManagementRecordValue, Builder> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.BATCH_OPERATION_LIFECYCLE_MANAGEMENT_CONFIG;
  }

  @Override
  public void transform(
      final Record<BatchOperationLifecycleManagementRecordValue> record, final Builder entity) {
    // no-op, batch operation key is already set in the common handler
    entity.tenantScope(AuditLogTenantScope.GLOBAL);
  }
}
