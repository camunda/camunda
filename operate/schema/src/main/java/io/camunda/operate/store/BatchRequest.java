/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import java.util.Map;

public interface BatchRequest {

  BatchRequest add(String index, OperateEntity entity) throws PersistenceException;

  BatchRequest addWithId(String index, String id, OperateEntity entity) throws PersistenceException;

  BatchRequest addWithRouting(String index, OperateEntity entity, String routing)
      throws PersistenceException;

  BatchRequest upsert(
      String index, String id, OperateEntity entity, Map<String, Object> updateFields)
      throws PersistenceException;

  BatchRequest upsertWithRouting(
      String index,
      String id,
      OperateEntity entity,
      Map<String, Object> updateFields,
      String routing)
      throws PersistenceException;

  BatchRequest update(String index, String id, Map<String, Object> updateFields)
      throws PersistenceException;

  BatchRequest update(String index, String id, OperateEntity entity) throws PersistenceException;

  BatchRequest updateWithScript(
      String index, String id, String script, Map<String, Object> parameters)
      throws PersistenceException;

  void execute() throws PersistenceException;

  void executeWithRefresh() throws PersistenceException;
}
