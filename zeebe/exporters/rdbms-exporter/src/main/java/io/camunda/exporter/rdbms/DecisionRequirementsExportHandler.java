/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.service.DecisionRequirementsWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import java.nio.charset.StandardCharsets;

public class DecisionRequirementsExportHandler
    implements RdbmsExportHandler<DecisionRequirementsRecordValue> {

  private final DecisionRequirementsWriter decisionRequirementsWriter;

  public DecisionRequirementsExportHandler(
      final DecisionRequirementsWriter decisionRequirementsWriter) {
    this.decisionRequirementsWriter = decisionRequirementsWriter;
  }

  @Override
  public boolean canExport(final Record<DecisionRequirementsRecordValue> record) {
    // do not react on DecisionRequirementsIntent.DELETED to keep historic data
    return record.getValueType() == ValueType.DECISION_REQUIREMENTS
        && record.getIntent() == DecisionRequirementsIntent.CREATED;
  }

  @Override
  public void export(final Record<DecisionRequirementsRecordValue> record) {
    final DecisionRequirementsRecordValue value = record.getValue();
    decisionRequirementsWriter.create(map(value));
  }

  private DecisionRequirementsDbModel map(final DecisionRequirementsRecordValue value) {
    return new DecisionRequirementsDbModel.Builder()
        .decisionRequirementsKey(value.getDecisionRequirementsKey())
        .decisionRequirementsId(value.getDecisionRequirementsId())
        .name(value.getDecisionRequirementsName())
        .version(value.getDecisionRequirementsVersion())
        .resourceName(value.getResourceName())
        .xml(new String(value.getResource(), StandardCharsets.UTF_8))
        .tenantId(value.getTenantId())
        .build();
  }
}
