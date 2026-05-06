/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;

public class HistoryDeletionAuditLogTransformer
    implements AuditLogTransformer<HistoryDeletionRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.HISTORY_DELETION_CONFIG;
  }

  @Override
  public void transform(final Record<HistoryDeletionRecordValue> record, final AuditLogEntry log) {
    final var value = record.getValue();

    log.setEntityKey(String.valueOf(value.getResourceKey()))
        .setEntityType(mapResourceTypeToEntityType(value.getResourceType()))
        .setEntityDescription(value.getResourceType().name());

    // Set specific key based on resource type
    switch (value.getResourceType()) {
      case PROCESS_INSTANCE -> log.setProcessInstanceKey(value.getResourceKey());
      case DECISION_INSTANCE -> log.setDecisionEvaluationKey(value.getResourceKey());
      case DECISION_REQUIREMENTS -> log.setDecisionRequirementsKey(value.getResourceKey());
      case PROCESS_DEFINITION -> log.setProcessDefinitionKey(value.getResourceKey());
    }

    // Set processDefinitionId for process-related deletions
    if (value.getProcessId() != null && !value.getProcessId().isEmpty()) {
      log.setProcessDefinitionId(value.getProcessId());
    }

    // Set decisionDefinitionId for decision-related deletions
    if (value.getDecisionDefinitionId() != null && !value.getDecisionDefinitionId().isEmpty()) {
      log.setDecisionDefinitionId(value.getDecisionDefinitionId());
    }
  }

  private AuditLogEntityType mapResourceTypeToEntityType(final HistoryDeletionType resourceType) {
    return switch (resourceType) {
      case PROCESS_INSTANCE -> AuditLogEntityType.PROCESS_INSTANCE;
      case DECISION_INSTANCE -> AuditLogEntityType.DECISION;
      case PROCESS_DEFINITION, DECISION_REQUIREMENTS -> AuditLogEntityType.RESOURCE;
    };
  }
}
