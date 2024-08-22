/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.zeebe.operate.exporter.handlers.ExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates Operate entities of different types and provide the method to flush them in batch.
 */
public class ExportBatchWriter {

  // static
  private final Map<ValueType, List<ExportHandler<?, ?>>> handlers = new HashMap<>();

  // dynamic
  private final Map<Tuple<String, Class<?>>, Tuple<OperateEntity, ExportHandler<?, ?>>>
      cachedEntities = new HashMap<>();
  private final Map<String, DecisionInstanceEntity> cachedDecisionInstanceEntities =
      new HashMap<>();

  public void addRecord(final Record<?> record) {
    // TODO: need to filter to only handle events
    final ValueType valueType = record.getValueType();

    handlers
        .getOrDefault(valueType, Collections.emptyList())
        .forEach(
            handler -> {
              // TODO: ugly, generic problem
              final ExportHandler handler2 = (ExportHandler) handler;

              if (handler.handlesRecord((Record) record)) {

                final List<String> entityIds = handler.generateIds((Record) record);
                entityIds.forEach(
                    id -> {
                      final Tuple<String, Class<?>> cacheKey =
                          new Tuple<>(id, handler.getEntityType());

                      final OperateEntity cachedEntity;

                      final boolean alreadyCached = cachedEntities.containsKey(cacheKey);
                      if (alreadyCached) {
                        cachedEntity = cachedEntities.get(cacheKey).getLeft();
                      } else {
                        cachedEntity = handler.createNewEntity(id);
                      }

                      handler2.updateEntity(record, cachedEntity);

                      // always store the latest handler in the tuple, because that is the one
                      // taking care of flushing
                      cachedEntities.put(cacheKey, new Tuple<>(cachedEntity, handler));
                    });
              }
            });
  }

  public void flush(final NewElasticsearchBatchRequest batchRequest) throws PersistenceException {
    // some handlers modify the same entity (e.g. list view flow node instances are
    // updated from process instance and incident records)
    //
    // the handler that modified the entity last will also flush it
    if (cachedEntities.size() > 0) {
      for (final Tuple<OperateEntity, ExportHandler<?, ?>> entityAndHandler :
          cachedEntities.values()) {
        final OperateEntity entity = entityAndHandler.getLeft();
        final ExportHandler handler = entityAndHandler.getRight();
        handler.flush(entity, batchRequest);
      }
      batchRequest.execute();
      reset();
    }
  }

  public void reset() {
    cachedEntities.clear();
    cachedDecisionInstanceEntities.clear();
  }

  public List<ExportHandler<?, ?>> getHandlersForValueType(final ValueType type) {
    return handlers.get(type);
  }

  public boolean hasAtLeastEntities(final int size) {
    return getNumCachedEntities() >= size;
  }

  public int getNumCachedEntities() {
    return cachedEntities.size() + cachedDecisionInstanceEntities.size();
  }

  public static class Builder {
    private ExportBatchWriter writer;

    public static Builder begin() {
      final Builder builder = new Builder();
      builder.writer = new ExportBatchWriter();
      return builder;
    }

    public <T extends OperateEntity<T>> Builder withHandler(final ExportHandler<?, ?> handler) {

      CollectionUtil.addToMap(
          writer.handlers, handler.getHandledValueType(), (ExportHandler) handler);

      return this;
    }

    public ExportBatchWriter build() {
      return writer;
    }
  }
}
