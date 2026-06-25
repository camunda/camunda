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
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.webapps.schema.entities.waitstate.WaitStateEntity;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateDetails;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared logic for all wait-state export handlers. Subclasses decide which records they handle and
 * how to flush the entity to the batch request.
 *
 * @param <R> the record value type handled by the injected transformer
 */
@NullMarked
public abstract class AbstractWaitStateHandler<R extends RecordValue & WaitStateRelated>
    implements ExportHandler<WaitStateEntity, R> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractWaitStateHandler.class);

  protected final String indexName;
  protected final WaitStateTransformer<R> transformer;
  protected final ObjectMapper objectMapper;

  protected AbstractWaitStateHandler(
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
        .setBpmnProcessId(entry.getBpmnProcessId())
        .setDetails(serializeDetails(entry.getDetails()))
        .setTenantId(entry.getTenantId())
        .setPartitionId(entry.getPartitionId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @VisibleForTesting
  public WaitStateTransformer<R> getTransformer() {
    return transformer;
  }

  protected @Nullable String serializeDetails(final @Nullable WaitStateDetails details) {
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
