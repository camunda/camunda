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
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class MetricFromDecisionEvaluationHandler
    implements ExportHandler<MetricEntity, DecisionEvaluationRecordValue> {

  protected static final String EVENT_DECISION_INSTANCE_EVALUATED =
      "EVENT_DECISION_INSTANCE_EVALUATED";
  private static final String ID_PATTERN = "%d-%d";

  private final String indexName;

  public MetricFromDecisionEvaluationHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.DECISION_EVALUATION;
  }

  @Override
  public Class<MetricEntity> getEntityType() {
    return MetricEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<DecisionEvaluationRecordValue> record) {
    return record.getIntent().name().equals(DecisionEvaluationIntent.EVALUATED.name())
        || record.getIntent().name().equals(DecisionEvaluationIntent.FAILED.name());
  }

  @Override
  public List<String> generateIds(final Record<DecisionEvaluationRecordValue> record) {
    final List<String> ids = new ArrayList<>();
    for (int i = 1; i <= record.getValue().getEvaluatedDecisions().size(); i++) {
      ids.add(String.format(ID_PATTERN, record.getKey(), i));
    }
    return ids;
  }

  @Override
  public MetricEntity createNewEntity(final String id) {
    return new MetricEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<DecisionEvaluationRecordValue> record, final MetricEntity entity) {
    final DecisionEvaluationRecordValue recordValue = record.getValue();
    final OffsetDateTime timestamp =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC);
    final String tenantId = tenantOrDefault(recordValue.getTenantId());
    final String value = entity.getId();
    entity
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(value)
        .setId(null)
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }

  @Override
  public void flush(final MetricEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
