/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
