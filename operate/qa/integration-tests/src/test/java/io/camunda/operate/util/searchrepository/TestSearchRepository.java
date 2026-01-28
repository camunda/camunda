/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.searchrepository;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.util.List;

public interface TestSearchRepository {

  boolean createOrUpdateDocumentFromObject(String indexName, String docId, Object data)
      throws IOException;

  boolean createOrUpdateDocumentFromObject(
      String indexName, String docId, Object data, String routing) throws IOException;

  String createOrUpdateDocumentFromObject(String indexName, Object data) throws IOException;

  <R> List<R> searchAll(String index, Class<R> clazz) throws IOException;

  <R> List<R> searchTerm(String index, String field, Object value, Class<R> clazz, int size)
      throws IOException;

  List<VariableEntity> getVariablesByProcessInstanceKey(String index, Long processInstanceKey);

  List<ProcessInstanceForListViewEntity> getProcessInstances(String indexName, List<Long> ids)
      throws IOException;
}
