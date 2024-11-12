/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.util.Map;

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

  void execute() throws PersistenceException;

  void executeWithRefresh() throws PersistenceException;
}
