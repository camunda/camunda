package io.camunda.zeebe.exporter.operate;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Set;

public interface ExportHandler<T extends OperateEntity<T>, R extends RecordValue> {

  ValueType handlesValueType();

  boolean handlesRecord(Record<R> record);

  String generateId(Record<R> record);

  T createNewEntity(String id);

  void updateEntity(Record<R> record, T entity);

  void flush(T entity, BatchRequest batchRequest) throws PersistenceException;
}
