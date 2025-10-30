/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.utils.EntitySizeEstimator;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Caches exporter entities of different types and provide the method to flush them in a batch. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class ExporterBatchWriter {
  private final Map<EntityIdAndEntityType, EntityAndHandlers> cachedEntities = new HashMap<>();
  private final Map<EntityIdAndEntityType, Long> cachedEntitySizes = new HashMap<>();
  private final Map<Long, Long> cachedRecordTimestamps = new HashMap<>();

  private final Map<ValueType, List<ExportHandler>> handlers;
  private final BiConsumer<String, Error> customErrorHandler;
  private final CamundaExporterMetrics metrics;

  private ExporterBatchWriter(
      final Map<ValueType, List<ExportHandler>> handlers,
      final BiConsumer<String, Error> customErrorHandler,
      final CamundaExporterMetrics metrics) {
    this.handlers = new HashMap<>(handlers);
    this.customErrorHandler = customErrorHandler;
    this.metrics = metrics;
  }

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

    final EntityAndHandlers entityAndHandlers =
        cachedEntities.computeIfAbsent(
            cacheKey,
            (k) -> {
              final ExporterEntity entity = handler.createNewEntity(id);
              return new EntityAndHandlers(entity, new LinkedHashSet<>());
            });

    final var entity = entityAndHandlers.entity;
    handler.updateEntity(record, entity);
    cachedRecordTimestamps.put(record.getPosition(), record.getTimestamp());

    cachedEntitySizes.put(cacheKey, EntitySizeEstimator.estimateEntitySize(entity));

    // we store all handlers for an entity to make sure not to miss any flushes
    entityAndHandlers.handlers.add(handler);
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
      for (final var handler : entityAndHandler.handlers()) {
        handler.flush(entity, batchRequest);
      }
    }

    batchRequest.execute(customErrorHandler);
    observeRecordTimestamps();
    reset();
  }

  public int getBatchMemoryEstimateInMb() {
    return (int) cachedEntitySizes.values().stream().mapToLong(Long::longValue).sum()
        / (1024 * 1024);
  }

  private void observeRecordTimestamps() {
    final var timestamps = new ArrayList<>(cachedRecordTimestamps.values());
    cachedRecordTimestamps.clear();
    metrics.observeRecordExportLatencies(timestamps);
  }

  public int getBatchSize() {
    return cachedEntities.size();
  }

  private void reset() {
    cachedEntities.clear();
    cachedEntitySizes.clear();
  }

  public static final class Builder {
    private final CamundaExporterMetrics metrics;
    private final Map<ValueType, List<ExportHandler>> handlers = new HashMap<>();
    private BiConsumer<String, Error> customErrorHandler = (ignored, error) -> {};

    private Builder(final CamundaExporterMetrics metrics) {
      this.metrics = metrics;
    }

    public static Builder begin(final CamundaExporterMetrics metrics) {
      return new Builder(metrics);
    }

    @VisibleForTesting
    static Builder begin() {
      return new Builder(new CamundaExporterMetrics(new SimpleMeterRegistry()));
    }

    public <T extends ExporterEntity<T>, R extends RecordValue> Builder withHandler(
        final ExportHandler<T, R> handler) {
      final Set<ValueType> valueTypes = new HashSet<>();
      final var handledValueTypes = handler.getHandledValueTypes();
      if (!handledValueTypes.isEmpty()) {
        valueTypes.addAll(handledValueTypes);
      }
      final var handledValueType = handler.getHandledValueType();
      if (handledValueType != null && handledValueType != ValueType.NULL_VAL) {
        valueTypes.add(handledValueType);
      }
      valueTypes.forEach(
          valueType -> handlers.computeIfAbsent(valueType, k -> new ArrayList<>()).add(handler));

      return this;
    }

    public ExporterBatchWriter build() {
      return new ExporterBatchWriter(handlers, customErrorHandler, metrics);
    }

    public Builder withCustomErrorHandlers(final BiConsumer<String, Error> customErrorHandler) {
      this.customErrorHandler = customErrorHandler;
      return this;
    }
  }

  private record EntityIdAndEntityType(String entityId, Class<?> entityType) {}

  private record EntityAndHandlers(ExporterEntity entity, Set<ExportHandler> handlers) {}
}
