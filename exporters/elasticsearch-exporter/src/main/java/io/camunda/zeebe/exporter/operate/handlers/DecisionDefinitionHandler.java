/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.schema.indices.DecisionIndex;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDefinitionHandler
    implements ExportHandler<DecisionDefinitionEntity, DecisionRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionDefinitionHandler.class);

  private static final Set<Intent> STATES = new HashSet<>();

  static {
    STATES.add(DecisionIntent.CREATED);
  }

  private DecisionIndex decisionIndex;

  public DecisionDefinitionHandler(DecisionIndex decisionIndex) {
    this.decisionIndex = decisionIndex;
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
  public boolean handlesRecord(Record<DecisionRecordValue> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public String generateId(Record<DecisionRecordValue> record) {
    return String.valueOf(record.getValue().getDecisionKey());
  }

  @Override
  public DecisionDefinitionEntity createNewEntity(String id) {
    return new DecisionDefinitionEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<DecisionRecordValue> record, DecisionDefinitionEntity entity) {
    final DecisionRecordValue decision = record.getValue();

    entity
        .setKey(decision.getDecisionKey())
        .setName(decision.getDecisionName())
        .setVersion(decision.getVersion())
        .setDecisionId(decision.getDecisionId())
        .setDecisionRequirementsId(decision.getDecisionRequirementsId())
        .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
        .setTenantId(tenantOrDefault(decision.getTenantId()));
  }

  @Override
  public void flush(DecisionDefinitionEntity entity, OperateElasticsearchBulkRequest batchRequest) {
    LOGGER.debug("Decision: key {}, decisionId {}", entity.getKey(), entity.getDecisionId());
    batchRequest.index(decisionIndex.getFullQualifiedName(), entity);
  }

  @Override
  public String getIndexName() {
    return decisionIndex.getFullQualifiedName();
  }
}
