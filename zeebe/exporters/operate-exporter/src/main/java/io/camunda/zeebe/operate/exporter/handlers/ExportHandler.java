/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

/**
 * Converts one type of Zeebe record to Operate entity (entities) of spesific type.
 *
 * @param <T>
 * @param <R>
 */
public interface ExportHandler<T extends OperateEntity, R extends RecordValue> {

  ValueType getHandledValueType();

  Class<T> getEntityType();

  boolean handlesRecord(Record<R> record);

  List<String> generateIds(Record<R> record);

  T createNewEntity(String id);

  void updateEntity(Record<R> record, T entity);

  void flush(T entity, NewElasticsearchBatchRequest batchRequest) throws PersistenceException;

  // for testing
  String getIndexName();
}
