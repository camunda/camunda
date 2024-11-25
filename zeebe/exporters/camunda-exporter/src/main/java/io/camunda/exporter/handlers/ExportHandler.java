/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

/**
 * Converts one type of Zeebe record to exporter entity (entities) of specific type.
 *
 * @param <T> the type of the entity that the handler creates or updates
 * @param <R> the type of the records that the handler can process
 */
public interface ExportHandler<T extends ExporterEntity<T>, R extends RecordValue> {

  /**
   * @return the value type of the record that the handler can process
   */
  ValueType getHandledValueType();

  /**
   * @return the entity type that the handler creates or updates
   */
  Class<T> getEntityType();

  /**
   * The handler need no process all records of the given value type. This method allows the handler
   * to filter out records that it does not need to process.
   *
   * @param record the record to check
   * @return true if the handler can process the record, false otherwise
   */
  boolean handlesRecord(Record<R> record);

  /**
   * Generates the id(s) for the entities that will be created or updated by the handler when
   * processing the given record.
   *
   * @param record the record to process
   * @return a list of ids for the entities
   */
  List<String> generateIds(Record<R> record);

  /**
   * Creates a new entity with the given id.
   *
   * @param id the id of the entity
   * @return the new entity
   */
  T createNewEntity(String id);

  /**
   * Updates the entity from the given record
   *
   * @param record to be processed by the handler
   * @param entity the entity which should be updated
   */
  void updateEntity(Record<R> record, T entity);

  /**
   * Adds the entity or update to the entity to the batch request.
   *
   * @param entity the entity to write to ElasticSearch or OpenSearch
   * @param batchRequest the batch request to add the entity to
   * @throws PersistenceException if the handler fails to flush the entity to the batch request
   */
  void flush(T entity, BatchRequest batchRequest) throws PersistenceException;

  /**
   * @return the index name that the handler entities are flushed to.
   */
  String getIndexName();
}
