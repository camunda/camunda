package io.camunda.zeebe.exporter.operate;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;

public interface ExportHandler<T extends OperateEntity<T>, R extends RecordValue> {

  ValueType getHandledValueType();

  Class<T> getEntityType();

  boolean handlesRecord(Record<R> record);

  String generateId(Record<R> record);

  T createNewEntity(String id);

  void updateEntity(Record<R> record, T entity);

  void flush(T entity, BatchRequest batchRequest) throws PersistenceException;

  // for testing

  String getIndexName();
}
