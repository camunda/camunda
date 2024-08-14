/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.exporter.entities.ExporterEntity;
import io.camunda.exporter.exceptions.PersistenceException;
import java.util.Map;

/** A {@link BatchRequest} contains updates to one or more {@link ExporterEntity} */
public interface BatchRequest {

  BatchRequest add(String index, ExporterEntity entity) throws PersistenceException;

  BatchRequest addWithId(String index, String id, ExporterEntity entity)
      throws PersistenceException;

  BatchRequest addWithRouting(String index, ExporterEntity entity, String routing)
      throws PersistenceException;

  BatchRequest upsert(
      String index, String id, ExporterEntity entity, Map<String, Object> updateFields)
      throws PersistenceException;

  BatchRequest upsertWithRouting(
      String index,
      String id,
      ExporterEntity entity,
      Map<String, Object> updateFields,
      String routing)
      throws PersistenceException;

  BatchRequest upsertWithScript(
      String index, String id, ExporterEntity entity, String script, Map<String, Object> parameters)
      throws PersistenceException;

  BatchRequest upsertWithScriptAndRouting(
      String index,
      String id,
      ExporterEntity entity,
      String script,
      Map<String, Object> parameters,
      String routing)
      throws PersistenceException;

  BatchRequest update(String index, String id, Map<String, Object> updateFields)
      throws PersistenceException;

  BatchRequest update(String index, String id, ExporterEntity entity) throws PersistenceException;

  BatchRequest updateWithScript(
      String index, String id, String script, Map<String, Object> parameters)
      throws PersistenceException;

  /**
   * Applies all updates in this batch.
   *
   * @throws PersistenceException if an error occurs during the execution
   */
  void execute() throws PersistenceException;

  void executeWithRefresh() throws PersistenceException;
}
