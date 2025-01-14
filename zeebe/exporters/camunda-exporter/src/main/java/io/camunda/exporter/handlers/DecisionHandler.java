/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import java.util.List;
import java.util.Set;

public class DecisionHandler
    implements ExportHandler<DecisionDefinitionEntity, DecisionRecordValue> {

  private static final Set<String> STATES = Set.of(ProcessIntent.CREATED.name());
  private final String indexName;

  public DecisionHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.DECISION;
  }

  @Override
  public Class<DecisionDefinitionEntity> getEntityType() {
    return DecisionDefinitionEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<DecisionRecordValue> record) {
    final String intentStr = record.getIntent().name();
    return STATES.contains(intentStr);
  }

  @Override
  public List<String> generateIds(final Record<DecisionRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getDecisionKey()));
  }

  @Override
  public DecisionDefinitionEntity createNewEntity(final String id) {
    return new DecisionDefinitionEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<DecisionRecordValue> record, final DecisionDefinitionEntity entity) {
    final DecisionRecordValue decision = record.getValue();
    entity
        .setId(String.valueOf(decision.getDecisionKey()))
        .setKey(decision.getDecisionKey())
        .setName(decision.getDecisionName())
        .setVersion(decision.getVersion())
        .setDecisionId(decision.getDecisionId())
        .setDecisionRequirementsId(decision.getDecisionRequirementsId())
        .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
        .setTenantId(tenantOrDefault(decision.getTenantId()));
  }

  @Override
  public void flush(final DecisionDefinitionEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
