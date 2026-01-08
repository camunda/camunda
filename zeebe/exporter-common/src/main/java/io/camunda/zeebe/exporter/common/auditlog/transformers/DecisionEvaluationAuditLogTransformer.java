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
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import java.util.List;
import java.util.Optional;

public class DecisionEvaluationAuditLogTransformer
    implements AuditLogTransformer<DecisionEvaluationRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.DECISION_EVALUATION_CONFIG;
  }

  @Override
  public void transform(
      final Record<DecisionEvaluationRecordValue> record, final AuditLogEntry log) {
    final var value = record.getValue();
    log.setDecisionDefinitionId(value.getDecisionId());
    log.setDecisionDefinitionKey(value.getDecisionKey());
    log.setDecisionRequirementsId(value.getDecisionRequirementsId());
    log.setDecisionRequirementsKey(value.getDecisionRequirementsKey());
    log.setDecisionEvaluationKey(
        Optional.ofNullable(value.getEvaluatedDecisions())
            .filter(list -> !list.isEmpty())
            .map(List::getFirst)
            .map(EvaluatedDecisionValue::getDecisionKey)
            .orElse(null));
    if (record.getIntent() == DecisionEvaluationIntent.FAILED) {
      log.setResult(io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.FAIL);
    }
  }
}
