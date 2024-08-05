/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionHandler
    implements ExportHandler<DecisionDefinitionEntity, DecisionRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionHandler.class);

  private static final Set<String> STATES = Set.of(ProcessIntent.CREATED.name());

  private final DecisionIndex decisionIndex;

  public DecisionHandler(DecisionIndex decisionIndex) {
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
    final String intentStr = record.getIntent().name();
    return STATES.contains(intentStr);
  }

  @Override
  public List<String> generateIds(Record<DecisionRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getDecisionKey()));
  }

  @Override
  public DecisionDefinitionEntity createNewEntity(String id) {
    return new DecisionDefinitionEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<DecisionRecordValue> record, DecisionDefinitionEntity entity) {
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
  public void flush(DecisionDefinitionEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.addWithId(getIndexName(), ConversionUtils.toStringOrNull(entity.getKey()), entity);
  }

  @Override
  public String getIndexName() {
    return decisionIndex.getFullQualifiedName();
  }
}
