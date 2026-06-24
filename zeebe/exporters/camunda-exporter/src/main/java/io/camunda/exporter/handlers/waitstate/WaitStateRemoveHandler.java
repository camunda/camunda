/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.waitstate;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.waitstate.WaitStateEntity;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;

/**
 * Deletes a {@link WaitStateEntity} from the wait-state index when a process element leaves its
 * waiting state (e.g. job completed, user task completed). The document is identified by the same
 * stable entity key used during insertion.
 *
 * @param <R> the record value type handled by the injected transformer
 */
public class WaitStateRemoveHandler<R extends RecordValue & WaitStateRelated>
    implements ExportHandler<WaitStateEntity, R> {

  private final String indexName;
  private final WaitStateTransformer<R> transformer;

  public WaitStateRemoveHandler(final String indexName, final WaitStateTransformer<R> transformer) {
    this.indexName = indexName;
    this.transformer = transformer;
  }

  @Override
  public ValueType getHandledValueType() {
    return transformer.config().valueType();
  }

  @Override
  public Class<WaitStateEntity> getEntityType() {
    return WaitStateEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    return transformer.triggersRemoval(record);
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public WaitStateEntity createNewEntity(final String id) {
    return new WaitStateEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final WaitStateEntity entity) {
    // no-op: only the id is needed to issue the delete
  }

  @Override
  public void flush(final WaitStateEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.delete(indexName, entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @VisibleForTesting
  public WaitStateTransformer<R> getTransformer() {
    return transformer;
  }
}
