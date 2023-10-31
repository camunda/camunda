/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TestSearchRepository {
  enum DynamicMappingType {
    Strict,
    True
  }

  boolean isConnected();

  boolean isZeebeConnected();

  boolean createIndex(String indexName, Map<String, ?> mapping) throws IOException, Exception;

  boolean createOrUpdateDocument(String indexName, String string, Map<String, String> doc) throws IOException;

  Set<String> getFieldNames(String indexName) throws IOException;

  boolean hasDynamicMapping(String indexName, DynamicMappingType dynamicMappingType) throws IOException;

  List<String> getAliasNames(String indexName) throws IOException;

  <R> List<R> searchAll(String index, Class<R> clazz) throws IOException;

  <R> List<R> searchJoinRelation(String index, String joinRelation, Class<R> clazz, int size) throws IOException;

  <A, R> List<R> searchTerm(String index, String field, A value, Class<R> clazz, int size) throws IOException;

  List<Long> searchIds(String index, String idFieldName, List<Long> ids, int size) throws IOException;
}
