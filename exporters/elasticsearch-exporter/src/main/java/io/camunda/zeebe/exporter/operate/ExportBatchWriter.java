package io.camunda.zeebe.exporter.operate;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ExportBatchWriter {

  // static
  private Map<ValueType, List<ExportHandler<?, ?>>> handlers = new HashMap<>();

  // dynamic
  private Map<String, OperateEntity<?>> cachedEntities = new HashMap<>();

  public void addRecord(Record<?> record) {
    ValueType valueType = record.getValueType();

    handlers
        .getOrDefault(valueType, Collections.emptyList())
        .forEach(
            handler -> {
              // TODO: lol ugly
              ExportHandler handler2 = (ExportHandler) handler;

              if (handler.handlesRecord((Record) record)) {

                String entityId = handler.generateId((Record) record);

                OperateEntity<?> cachedEntity =
                    cachedEntities.computeIfAbsent(entityId, handler::createNewEntity);

                handler2.updateEntity((Record) record, (OperateEntity) cachedEntity);
              }
            });

    Intent intent = record.getIntent();
  }

  public void flush() {
    // TODO: flush here to an ES bulk request
  }

  public static class Builder {
    private ExportBatchWriter writer = new ExportBatchWriter();

    public <T extends OperateEntity<T>> Builder withHandler(ExportHandler<?, ?> handler) {

      CollectionUtil.addToMap(writer.handlers, handler.handlesValueType(), (ExportHandler) handler);
      
      return this;
    }

    public ExportBatchWriter build() {
      return writer;
    }
  }
}
