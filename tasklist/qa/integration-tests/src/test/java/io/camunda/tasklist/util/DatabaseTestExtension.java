/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.RecordsReader;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.Extension;

public interface DatabaseTestExtension extends Extension {

  void setIndexMaxTermsCount(final String indexName, final int maxTermsCount) throws IOException;

  int getIndexMaxTermsCount(final String indexName) throws IOException;

  void assertMaxOpenScrollContexts(final int maxOpenScrollContexts);

  void refreshIndexesInElasticsearch();

  void refreshZeebeIndices();

  void refreshTasklistIndices();

  void processAllRecordsAndWait(TestCheck testCheck, Object... arguments);

  void processAllRecordsAndWait(
      TestCheck testCheck, Supplier<Object> supplier, Object... arguments);

  void processRecordsWithTypeAndWait(
      ImportValueType importValueType, TestCheck testCheck, Object... arguments);

  void processRecordsAndWaitFor(
      Collection<RecordsReader> readers,
      TestCheck testCheck,
      Supplier<Object> supplier,
      Object... arguments);

  boolean areIndicesCreatedAfterChecks(String indexPrefix, int minCountOfIndices, int maxChecks);

  List<RecordsReader> getRecordsReaders(ImportValueType importValueType);

  int getOpenScrollcontextSize();

  <T> long deleteByTermsQuery(
      String index, String fieldName, Collection<T> values, final Class<T> valueType)
      throws IOException;

  void reindex(String sourceIndex, String destinationIndex) throws IOException;

  void createIndex(String indexName) throws IOException;

  void deleteIndex(String indexName);
}
