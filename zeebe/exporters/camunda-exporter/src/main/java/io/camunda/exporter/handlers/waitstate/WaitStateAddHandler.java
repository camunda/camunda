/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.waitstate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.waitstate.WaitStateEntity;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateDetails;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes a {@link WaitStateEntity} to the wait-state index when a process element enters a waiting
 * state. The document is upserted using the stable entity key (e.g. jobKey, userTaskKey) as the
 * document id, so re-entering the same wait state overwrites the previous entry.
 *
 * @param <R> the record value type handled by the injected transformer
 */
public class WaitStateAddHandler<R extends RecordValue & WaitStateRelated>
    implements ExportHandler<WaitStateEntity, R> {

  private static final Logger LOG = LoggerFactory.getLogger(WaitStateAddHandler.class);

  private final String indexName;
  private final WaitStateTransformer<R> transformer;
  private final ObjectMapper objectMapper;

  public WaitStateAddHandler(
      final String indexName,
      final WaitStateTransformer<R> transformer,
      final ObjectMapper objectMapper) {
    this.indexName = indexName;
    this.transformer = transformer;
    this.objectMapper = objectMapper;
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
    return transformer.triggersAdd(record);
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
    final var entry = transformer.transform(record);

    entity
        .setRootProcessInstanceKey(entry.getRootProcessInstanceKey())
        .setProcessInstanceKey(entry.getProcessInstanceKey())
        .setElementInstanceKey(entry.getElementInstanceKey())
        .setElementId(entry.getElementId())
        .setElementType(entry.getElementType() != null ? entry.getElementType().name() : null)
        .setWaitStateType(entry.getWaitStateType() != null ? entry.getWaitStateType().name() : null)
        .setDetails(serializeDetails(entry.getDetails()))
        .setTenantId(entry.getTenantId())
        .setPartitionId(entry.getPartitionId());
  }

  @Override
  public void flush(final WaitStateEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @VisibleForTesting
  public WaitStateTransformer<R> getTransformer() {
    return transformer;
  }

  private String serializeDetails(final WaitStateDetails details) {
    if (details == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(details);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize wait state details: {}", e.getMessage(), e);
      return null;
    }
  }
}
