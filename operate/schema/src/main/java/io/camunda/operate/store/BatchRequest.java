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

  BatchRequest update(String index, String id, Map<String, Object> updateFields)
      throws PersistenceException;

  BatchRequest update(String index, String id, ExporterEntity entity) throws PersistenceException;

  /**
   * Updates the document with the given id in the given index using the provided script and
   * parameters.
   *
   * <p>Warning! If the document with the given id does not exist in the given index, this update
   * will fail exceptionally, resulting in a blocked exporter until the document is restored. For
   * some indices this exception will be swallowed. The list of these indices can be found in {@link
   * io.camunda.exporter.DefaultExporterResourceProvider#init}. See also <a
   * href="https://github.com/camunda/camunda/issues/44356">https://github.com/camunda/camunda/issues/44356</a>
   */
  BatchRequest updateWithScript(
      String index, String id, String script, Map<String, Object> parameters)
      throws PersistenceException;

  void execute() throws PersistenceException;

  void executeWithRefresh() throws PersistenceException;
}
