/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Caches exporter entities of different types and provide the method to flush them in a batch. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ExporterBatchWriter {
  private final Map<ValueType, List<ExportHandler>> handlers = new HashMap<>();
  private final Map<EntityIdAndEntityType, EntityAndHandler> cachedEntities = new HashMap<>();

  public void addRecord(final Record<?> record) {
    final ValueType valueType = record.getValueType();

    handlers
        .getOrDefault(valueType, Collections.emptyList())
        .forEach(
            handler -> {
              if (handler.handlesRecord(record)) {
                final List<String> entityIds = handler.generateIds(record);
                entityIds.forEach(id -> updateAndCacheEntity(record, handler, id));
              }
            });
  }

  private void updateAndCacheEntity(
      final Record<?> record, final ExportHandler handler, final String id) {
    final var cacheKey = new EntityIdAndEntityType(id, handler.getEntityType());

    final ExporterEntity entity;

    final boolean alreadyCached = cachedEntities.containsKey(cacheKey);
    if (alreadyCached) {
      entity = cachedEntities.get(cacheKey).entity();
    } else {
      entity = handler.createNewEntity(id);
    }

    handler.updateEntity(record, entity);

    // always store the latest handler in the tuple, because that is the one
    // taking care of flushing
    cachedEntities.put(cacheKey, new EntityAndHandler(entity, handler));
  }

  public void flush(final BatchRequest batchRequest) throws PersistenceException {
    // some handlers modify the same entity (e.g. list view flow node instances are
    // updated from process instance and incident records)
    //
    // the handler that modified the entity last will also flush it
    if (cachedEntities.isEmpty()) {
      return;
    }

    for (final var entityAndHandler : cachedEntities.values()) {
      final ExporterEntity entity = entityAndHandler.entity();
      final ExportHandler handler = entityAndHandler.handler();
      handler.flush(entity, batchRequest);
    }
    batchRequest.execute();
    reset();
  }

  public void reset() {
    cachedEntities.clear();
  }

  public int getBatchSize() {
    return cachedEntities.size();
  }

  public static class Builder {
    private ExporterBatchWriter writer;

    public static Builder begin() {
      final Builder builder = new Builder();
      builder.writer = new ExporterBatchWriter();
      return builder;
    }

    public <T extends ExporterEntity<T>, R extends RecordValue> Builder withHandler(
        final ExportHandler<T, R> handler) {
      writer
          .handlers
          .computeIfAbsent(handler.getHandledValueType(), k -> new ArrayList<>())
          .add(handler);

      return this;
    }

    public ExporterBatchWriter build() {
      return writer;
    }
  }

  private record EntityIdAndEntityType(String entityId, Class<?> entityType) {}

  private record EntityAndHandler(ExporterEntity entity, ExportHandler handler) {}
}
