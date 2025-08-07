/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.searchrepository;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface TestSearchRepository {
  boolean isConnected();

  boolean isZeebeConnected();

  boolean createIndex(String indexName, Map<String, ?> mapping) throws Exception;

  boolean createOrUpdateDocumentFromObject(String indexName, String docId, Object data)
      throws IOException;

  boolean createOrUpdateDocumentFromObject(
      String indexName, String docId, Object data, String routing) throws IOException;

  String createOrUpdateDocumentFromObject(String indexName, Object data) throws IOException;

  boolean createOrUpdateDocument(String indexName, String docId, Map<String, ?> doc)
      throws IOException;

  boolean createOrUpdateDocument(String name, String id, Map<String, ?> doc, String routing)
      throws IOException;

  String createOrUpdateDocument(String indexName, Map<String, ?> doc) throws IOException;

  void deleteById(String index, String id) throws IOException;

  Set<String> getFieldNames(String indexName) throws IOException;

  boolean hasDynamicMapping(String indexName, DynamicMappingType dynamicMappingType)
      throws IOException;

  List<String> getAliasNames(String indexName) throws IOException;

  <R> List<R> searchAll(String index, Class<R> clazz) throws IOException;

  <R> List<R> searchJoinRelation(String index, String joinRelation, Class<R> clazz, int size)
      throws IOException;

  <R> List<R> searchTerm(String index, String field, Object value, Class<R> clazz, int size)
      throws IOException;

  <R> List<R> searchTerms(String index, Map<String, Object> fieldValueMap, Class<R> clazz, int size)
      throws IOException;

  List<Long> searchIds(String index, String idFieldName, List<Long> ids, int size)
      throws IOException;

  void deleteByTermsQuery(String index, String fieldName, List<Long> values) throws IOException;

  void update(String index, String id, Map<String, Object> fields) throws IOException;

  List<VariableEntity> getVariablesByProcessInstanceKey(String index, Long processInstanceKey);

  void reindex(String srcIndex, String dstIndex, String script, Map<String, Object> scriptParams)
      throws IOException;

  boolean ilmPolicyExists(String policyName) throws IOException;

  IndexSettings getIndexSettings(String indexName) throws IOException;

  List<BatchOperationEntity> getBatchOperationEntities(String indexName, List<String> ids)
      throws IOException;

  List<ProcessInstanceForListViewEntity> getProcessInstances(String indexName, List<Long> ids)
      throws IOException;

  Optional<List<Long>> getIds(
      String indexName, String idFieldName, List<Long> ids, boolean ignoreAbsentIndex)
      throws IOException;

  Long getIndexTemplatePriority(String templateName);

  record IndexSettings(Integer shards, Integer replicas) {}

  enum DynamicMappingType {
    Strict,
    True
  }
}
