package io.camunda.zeebe.exporter.operate;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.zeebe.exporter.operate.handlers.DecisionInstanceHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExportBatchWriter {

  // static
  private Map<ValueType, List<ExportHandler<?, ?>>> handlers = new HashMap<>();
  private DecisionInstanceHandler decisionInstanceHandler;

  // dynamic
  // TODO: we should preserve the order during the flush (i.e. flush entities in the order they were
  // created)
  private Map<Tuple<String, Class<?>>, Tuple<OperateEntity<?>, ExportHandler<?, ?>>> cachedEntities =
      new HashMap<>();
  private Map<String, DecisionInstanceEntity> cachedDecisionInstanceEntities = new HashMap<>();

  private List<Tuple<String, Class<?>>> idFlushOrder = new ArrayList<>();

  private BatchRequest request;

  public ExportBatchWriter(BatchRequest request) {
    this.request = request;
  }

  public void addRecord(Record<?> record) {
    // TODO: need to filter to only handle events

    ValueType valueType = record.getValueType();

    if (valueType == decisionInstanceHandler.handlesValueType()) {
      List<DecisionInstanceEntity> entities =
          decisionInstanceHandler.createEntities((Record) record);

      // reorganize the entities for flushing them in order of creation
      entities.forEach(e -> cachedDecisionInstanceEntities.put(e.getId(), e));
      idFlushOrder.addAll(
          entities.stream().map(e -> (Tuple) new Tuple<>(e.getId(), DecisionInstanceEntity.class))
              .collect(Collectors.toList()));
    }

    handlers.getOrDefault(valueType, Collections.emptyList()).forEach(handler -> {
      // TODO: lol ugly
      ExportHandler handler2 = (ExportHandler) handler;

      if (handler.handlesRecord((Record) record)) {

        String entityId = handler.generateId((Record) record);
        Tuple<String, Class<?>> cacheKey = new Tuple<>(entityId, handler.getEntityType());

        OperateEntity<?> cachedEntity;
        if (cachedEntities.containsKey(cacheKey)) {
          cachedEntity = cachedEntities.get(cacheKey).getLeft();
        } else {
          cachedEntity = handler.createNewEntity(entityId);
        }

        handler2.updateEntity((Record) record, (OperateEntity) cachedEntity);

        // always store the latest handler in the tuple, because that is the one taking care of
        // flushing
        cachedEntities.put(cacheKey, new Tuple<>(cachedEntity, handler));

        // append the id to the end of the flush order (not particularly efficient, but should be
        // fine for prototyping)
        idFlushOrder.remove(cacheKey);
        idFlushOrder.add(cacheKey);
      }
    });

  }

  public void flush() throws PersistenceException {
    // TODO: flush here to an ES bulk request
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
        Tuple<OperateEntity<?>, ExportHandler<?, ?>> entityAndHandler =
            cachedEntities.get(cacheKey);
        OperateEntity entity = entityAndHandler.getLeft();
        ExportHandler handler = entityAndHandler.getRight();

        handler.flush(entity, request);

      } else {
        DecisionInstanceEntity entity = cachedDecisionInstanceEntities.get(cacheKey.getLeft());
        decisionInstanceHandler.flush(entity, request);
      }
    }
  }

  public static class Builder {
    private ExportBatchWriter writer;

    public static Builder forRequest(BatchRequest request) {
      Builder builder = new Builder();
      builder.writer = new ExportBatchWriter(request);
      return builder;
    }

    public <T extends OperateEntity<T>> Builder withHandler(ExportHandler<?, ?> handler) {

      CollectionUtil.addToMap(writer.handlers, handler.getHandledValueType(),
          (ExportHandler) handler);

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

  public boolean hasAtLeastEntities(int size) {
    return cachedEntities.size() + cachedDecisionInstanceEntities.size() >= size;
  }
}
