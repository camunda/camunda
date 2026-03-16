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
    log.setDecisionDefinitionId(value.getDecisionId())
        .setDecisionDefinitionKey(value.getDecisionKey())
        .setDecisionRequirementsId(value.getDecisionRequirementsId())
        .setDecisionRequirementsKey(value.getDecisionRequirementsKey());

    if (value.getEvaluatedDecisions() != null) {
      value.getEvaluatedDecisions().stream()
          .filter(
              ed ->
                  ed.getDecisionEvaluationInstanceKey() != null
                      && !ed.getDecisionEvaluationInstanceKey().isEmpty())
          .findFirst()
          .ifPresent(
              ed ->
                  log.setEntityKey(ed.getDecisionEvaluationInstanceKey())
                      .setDecisionEvaluationKey(ed.getDecisionKey()));
    }

    if (record.getIntent() == DecisionEvaluationIntent.FAILED) {
      log.setEntityDescription(record.getRejectionType().name());
      log.setResult(io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.FAIL);
    }
  }

  @Override
  public boolean supports(final Record<DecisionEvaluationRecordValue> record) {
    final var decisionEvaluation = record.getValue();
    final var isStandaloneDecision = decisionEvaluation.getProcessDefinitionKey() == -1L;
    return isStandaloneDecision && AuditLogTransformer.super.supports(record);
  }
}
