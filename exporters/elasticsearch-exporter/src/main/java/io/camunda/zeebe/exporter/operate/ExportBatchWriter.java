/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.zeebe.exporter.operate.handlers.DecisionInstanceHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExportBatchWriter {

  // static
  private Map<ValueType, List<ExportHandler<?, ?>>> handlers = new HashMap<>();
  private DecisionInstanceHandler decisionInstanceHandler;

  // dynamic
  // TODO: we should preserve the order during the flush (i.e. flush entities in the order they were
  // created)
  private Map<Tuple<String, Class<?>>, Tuple<OperateEntity<?>, ExportHandler<?, ?>>>
      cachedEntities = new HashMap<>();
  private Map<String, DecisionInstanceEntity> cachedDecisionInstanceEntities = new HashMap<>();

  private List<Tuple<String, Class<?>>> idFlushOrder = new ArrayList<>();

  public void addRecord(Record<?> record) {
    // TODO: need to filter to only handle events

    final ValueType valueType = record.getValueType();

    if (valueType == decisionInstanceHandler.handlesValueType()) {
      final List<DecisionInstanceEntity> entities =
          decisionInstanceHandler.createEntities((Record) record);

      // reorganize the entities for flushing them in order of creation
      entities.forEach(e -> cachedDecisionInstanceEntities.put(e.getId(), e));
      idFlushOrder.addAll(
          entities.stream()
              .map(e -> new Tuple<String, Class<?>>(e.getId(), DecisionInstanceEntity.class))
              .collect(Collectors.toList()));
    }

    handlers
        .getOrDefault(valueType, Collections.emptyList())
        .forEach(
            handler -> {
              // TODO: lol ugly
              final ExportHandler handler2 = (ExportHandler) handler;

              if (handler.handlesRecord((Record) record)) {

                final String entityId = handler.generateId((Record) record);
                final Tuple<String, Class<?>> cacheKey =
                    new Tuple<>(entityId, handler.getEntityType());

                final OperateEntity<?> cachedEntity;

                final boolean alreadyCached = cachedEntities.containsKey(cacheKey);
                if (alreadyCached) {
                  cachedEntity = cachedEntities.get(cacheKey).getLeft();
                } else {
                  cachedEntity = handler.createNewEntity(entityId);
                }

                handler2.updateEntity((Record) record, (OperateEntity) cachedEntity);

                // always store the latest handler in the tuple, because that is the one taking care
                // of
                // flushing
                cachedEntities.put(cacheKey, new Tuple<>(cachedEntity, handler));

                // append the id to the end of the flush order (not particularly efficient, but
                // should be
                // fine for prototyping)
                // TODO: removed this to avoid the performance penalty; it's not clear to me that
                // this is
                // strictly needed, the order in which documents will appear in ES for one batch
                // will be
                // slightly different now
                //                idFlushOrder.remove(cacheKey);
                if (!alreadyCached) {
                  idFlushOrder.add(cacheKey);
                }
              }
            });
  }

  public void flush(OperateElasticsearchBulkRequest request) {
    // TODO: consider that some handlers modify the same entity (e.g. list view flow node instances
    // are
    // updated from process instance and incident records); either change this
    // (i.e. let one handler react to more than one record type), or otherwise resolve this
    // duplication

    // TODO: maybe we can solve this by always letting the handler that created the entity also
    // flush it;
    // alternatively the handler that modified it last

    for (Tuple<String, Class<?>> cacheKey : idFlushOrder) {
      if (cachedEntities.containsKey(cacheKey)) {
        final Tuple<OperateEntity<?>, ExportHandler<?, ?>> entityAndHandler =
            cachedEntities.get(cacheKey);
        final OperateEntity entity = entityAndHandler.getLeft();
        final ExportHandler handler = entityAndHandler.getRight();

        handler.flush(entity, request);

      } else {
        final DecisionInstanceEntity entity =
            cachedDecisionInstanceEntities.get(cacheKey.getLeft());
        decisionInstanceHandler.flush(entity, request);
      }
    }

    reset();
  }

  private void reset() {
    cachedEntities.clear();
    cachedDecisionInstanceEntities.clear();
    idFlushOrder.clear();
  }

  public List<ExportHandler<?, ?>> getHandlersForValueType(ValueType type) {
    return handlers.get(type);
  }

  public boolean hasAtLeastEntities(int size) {
    return cachedEntities.size() + cachedDecisionInstanceEntities.size() >= size;
  }

  public static class Builder {
    private ExportBatchWriter writer;

    public static Builder begin() {
      final Builder builder = new Builder();
      builder.writer = new ExportBatchWriter();
      return builder;
    }

    public <T extends OperateEntity<T>> Builder withHandler(ExportHandler<?, ?> handler) {

      CollectionUtil.addToMap(
          writer.handlers, handler.getHandledValueType(), (ExportHandler) handler);

      return this;
    }

    public Builder withDecisionInstanceHandler(DecisionInstanceHandler handler) {
      writer.decisionInstanceHandler = handler;
      return this;
    }

    public ExportBatchWriter build() {
      return writer;
    }
  }
}
