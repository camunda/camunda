/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel.Builder;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerConfigs;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import java.util.List;
import java.util.Optional;

public class DecisionEvaluationAuditLogTransformer
    implements AuditLogTransformer<DecisionEvaluationRecordValue, Builder> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.DECISION_EVALUATION_CONFIG;
  }

  @Override
  public void transform(final Record<DecisionEvaluationRecordValue> record, final Builder entity) {
    final var value = record.getValue();
    entity.decisionDefinitionId(value.getDecisionId());
    entity.decisionDefinitionKey(value.getDecisionKey());
    entity.decisionRequirementsId(value.getDecisionRequirementsId());
    entity.decisionRequirementsKey(value.getDecisionRequirementsKey());
    entity.decisionEvaluationKey(
        Optional.ofNullable(value.getEvaluatedDecisions())
            .filter(list -> !list.isEmpty())
            .map(List::getFirst)
            .map(EvaluatedDecisionValue::getDecisionKey)
            .orElse(null));
    entity.tenantId(value.getTenantId());
    entity.tenantScope(AuditLogTenantScope.TENANT);
    if (record.getIntent() == DecisionEvaluationIntent.FAILED) {
      entity.result(AuditLogOperationResult.FAIL);
    }
  }
}
