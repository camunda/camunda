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
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DecisionRequirementsHandler
    implements ExportHandler<DecisionRequirementsEntity, DecisionRequirementsRecordValue> {
  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private final String indexName;

  public DecisionRequirementsHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.DECISION_REQUIREMENTS;
  }

  @Override
  public Class<DecisionRequirementsEntity> getEntityType() {
    return DecisionRequirementsEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<DecisionRequirementsRecordValue> record) {
    return record.getIntent().equals(DecisionRequirementsIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<DecisionRequirementsRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getDecisionRequirementsKey()));
  }

  @Override
  public DecisionRequirementsEntity createNewEntity(final String id) {
    return new DecisionRequirementsEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<DecisionRequirementsRecordValue> record,
      final DecisionRequirementsEntity entity) {
    final DecisionRequirementsRecordValue decisionRequirements = record.getValue();
    final byte[] byteArray = decisionRequirements.getResource();
    final String dmn = new String(byteArray, CHARSET);
    entity
        .setId(String.valueOf(decisionRequirements.getDecisionRequirementsKey()))
        .setKey(decisionRequirements.getDecisionRequirementsKey())
        .setName(decisionRequirements.getDecisionRequirementsName())
        .setDecisionRequirementsId(decisionRequirements.getDecisionRequirementsId())
        .setVersion(decisionRequirements.getDecisionRequirementsVersion())
        .setResourceName(decisionRequirements.getResourceName())
        .setXml(dmn)
        .setTenantId(tenantOrDefault(decisionRequirements.getTenantId()));
  }

  @Override
  public void flush(final DecisionRequirementsEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
