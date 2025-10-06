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
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.util.Map;
import java.util.function.BiConsumer;

/** A {@link BatchRequest} contains updates to one or more {@link ExporterEntity} */
@SuppressWarnings("rawtypes")
public interface BatchRequest {

  BatchRequest withMetrics(final CamundaExporterMetrics metrics);

  BatchRequest add(String index, ExporterEntity entity);

  BatchRequest addWithId(String index, String id, ExporterEntity entity);

  BatchRequest addWithRouting(String index, ExporterEntity entity, String routing);

  BatchRequest upsert(
      String index, String id, ExporterEntity entity, Map<String, Object> updateFields);

  BatchRequest upsertWithRouting(
      String index,
      String id,
      ExporterEntity entity,
      Map<String, Object> updateFields,
      String routing);

  BatchRequest upsertWithScript(
      String index,
      String id,
      ExporterEntity entity,
      String script,
      Map<String, Object> parameters);

  BatchRequest upsertWithScriptAndRouting(
      String index,
      String id,
      ExporterEntity entity,
      String script,
      Map<String, Object> parameters,
      String routing);

  BatchRequest update(String index, String id, Map<String, Object> updateFields);

  BatchRequest update(String index, String id, ExporterEntity entity) throws PersistenceException;

  BatchRequest updateWithScript(
      String index, String id, String script, Map<String, Object> parameters);

  BatchRequest delete(String index, String id);

  BatchRequest deleteWithRouting(String index, String id, String routing);

  /**
   * Deletes documents by a specific field value. Note that this can delete multiple documents if
   * more than 1 document matches the field value.
   */
  BatchRequest deleteByField(String index, String field, String value);

  /**
   * Applies all updates in this batch.
   *
   * @param customErrorHandlers possible custom error handlers to be used if certain indices threw
   *     persistence errors. The first parameter is the index name and the second is the error
   *     detail if not passed, a PersistenceException will be thrown by default in case of error
   * @throws PersistenceException if an error occurs during the execution
   */
  void execute(final BiConsumer<String, Error> customErrorHandlers) throws PersistenceException;

  default void execute() throws PersistenceException {
    execute(null);
  }

  void executeWithRefresh() throws PersistenceException;
}
