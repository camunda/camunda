/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionRequirementsHandler
    implements ExportHandler<DecisionRequirementsEntity, DecisionRequirementsRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionRequirementsHandler.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private static final Set<Intent> STATES = new HashSet<>();

  static {
    STATES.add(DecisionRequirementsIntent.CREATED);
  }

  private DecisionRequirementsIndex decisionRequirementsIndex;

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
    return STATES.contains(record.getIntent());
  }

  @Override
  public String generateId(Record<DecisionRequirementsRecordValue> record) {
    return String.valueOf(record.getValue().getDecisionRequirementsKey());
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
        .setKey(decisionRequirements.getDecisionRequirementsKey())
        .setName(decisionRequirements.getDecisionRequirementsName())
        .setDecisionRequirementsId(decisionRequirements.getDecisionRequirementsId())
        .setVersion(decisionRequirements.getDecisionRequirementsVersion())
        .setResourceName(decisionRequirements.getResourceName())
        .setXml(dmn)
        .setTenantId(tenantOrDefault(decisionRequirements.getTenantId()));
  }

  @Override
  public void flush(DecisionRequirementsEntity entity, BatchRequest batchRequest)
      throws PersistenceException {
    LOGGER.debug(
        "Process: key {}, decisionRequirementsId {}",
        entity.getKey(),
        entity.getDecisionRequirementsId());

    batchRequest.addWithId(
        decisionRequirementsIndex.getFullQualifiedName(),
        ConversionUtils.toStringOrNull(entity.getKey()),
        entity);
  }

  @Override
  public String getIndexName() {
    return decisionRequirementsIndex.getFullQualifiedName();
  }
}
