/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.service.DecisionDefinitionWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDefinitionExportHandler implements RdbmsExportHandler<DecisionRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionDefinitionExportHandler.class);

  private final DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionExportHandler(final DecisionDefinitionWriter decisionDefinitionWriter) {
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  public boolean canExport(final Record<DecisionRecordValue> record) {
    // do not react on DecisionIntent.DELETED to keep historic data
    return record.getValueType() == ValueType.DECISION
        && record.getIntent() == DecisionIntent.CREATED;
  }

  @Override
  public void export(final Record<DecisionRecordValue> record) {
    final DecisionRecordValue value = record.getValue();
    decisionDefinitionWriter.create(map(value));
  }

  private DecisionDefinitionDbModel map(final DecisionRecordValue decision) {
    return new DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder()
        .decisionDefinitionId(decision.getDecisionId())
        .decisionDefinitionKey(decision.getDecisionKey())
        .name(decision.getDecisionName())
        .version(decision.getVersion())
        .decisionRequirementsId(decision.getDecisionRequirementsId())
        .decisionRequirementsKey(decision.getDecisionRequirementsKey())
        .tenantId(decision.getTenantId())
        .build();
  }
}
