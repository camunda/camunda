/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.toOffsetDateTime;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.time.Instant;
import java.util.List;

public abstract class AbstractCorrelatedMessageSubscriptionHandler<T extends RecordValue>
    implements ExportHandler<CorrelatedMessageSubscriptionEntity, T> {

  protected final ExporterMetadata exporterMetadata;

  protected AbstractCorrelatedMessageSubscriptionHandler(final ExporterMetadata exporterMetadata) {
    this.exporterMetadata = exporterMetadata;
  }

  protected abstract long getMessageKey(T value);

  protected abstract void updateEntityFromRecordValue(
      final T value, final CorrelatedMessageSubscriptionEntity entity);

  @Override
  public Class<CorrelatedMessageSubscriptionEntity> getEntityType() {
    return CorrelatedMessageSubscriptionEntity.class;
  }

  @Override
  public List<String> generateIds(final Record<T> record) {
    exporterMetadata.setFirstCorrelatedMessageSubscriptionKey(record.getKey());

    if (exporterMetadata.keyIsBeforeFirstCorrelatedMessageSubscriptionKey(record.getKey())) {
      // composite id: messageKey_subscriptionKey
      return List.of("%s_%s".formatted(getMessageKey(record.getValue()), record.getKey()));
    }
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public CorrelatedMessageSubscriptionEntity createNewEntity(final String id) {
    return new CorrelatedMessageSubscriptionEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<T> record, final CorrelatedMessageSubscriptionEntity entity) {

    entity
        .setCorrelationTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setSubscriptionKey(record.getKey());

    updateEntityFromRecordValue(record.getValue(), entity);
  }

  @Override
  public void flush(
      final CorrelatedMessageSubscriptionEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(getIndexName(), entity);
  }
}
