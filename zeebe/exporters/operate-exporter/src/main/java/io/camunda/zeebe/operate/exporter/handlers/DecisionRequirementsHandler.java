/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionRequirementsHandler
    implements ExportHandler<DecisionRequirementsEntity, DecisionRequirementsRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionRequirementsHandler.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private static final Set<String> STATES = Set.of(ProcessIntent.CREATED.name());

  private final DecisionRequirementsIndex decisionRequirementsIndex;

  public DecisionRequirementsHandler(DecisionRequirementsIndex decisionRequirementsIndex) {
    this.decisionRequirementsIndex = decisionRequirementsIndex;
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
  public boolean handlesRecord(Record<DecisionRequirementsRecordValue> record) {
    final String intentStr = record.getIntent().name();
    return STATES.contains(intentStr);
  }

  @Override
  public List<String> generateIds(Record<DecisionRequirementsRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getDecisionRequirementsKey()));
  }

  @Override
  public DecisionRequirementsEntity createNewEntity(String id) {
    return new DecisionRequirementsEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<DecisionRequirementsRecordValue> record, DecisionRequirementsEntity entity) {
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
  public void flush(DecisionRequirementsEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.addWithId(getIndexName(), ConversionUtils.toStringOrNull(entity.getKey()), entity);
  }

  @Override
  public String getIndexName() {
    return decisionRequirementsIndex.getFullQualifiedName();
  }
}
