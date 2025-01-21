/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.RecordsReader;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.runner.Description;

public interface SearchTestRuleProvider {
  void failed(Throwable e, Description description);

  void starting(Description description);

  void finished(Description description);

  void assertMaxOpenScrollContexts(final int maxOpenScrollContexts);

  void refreshSearchIndices();

  void refreshZeebeIndices();

  void refreshOperateSearchIndices();

  void processAllRecordsAndWait(
      Integer maxWaitingRounds, Predicate<Object[]> predicate, Object... arguments);

  void processAllRecordsAndWait(Predicate<Object[]> predicate, Object... arguments);

  void processAllRecordsAndWait(
      Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments);

  void processRecordsAndWaitFor(
      Collection<RecordsReader> readers,
      Integer maxWaitingRounds,
      Predicate<Object[]> predicate,
      Supplier<Object> supplier,
      Object... arguments);

  boolean areIndicesCreatedAfterChecks(String indexPrefix, int minCountOfIndices, int maxChecks);

  List<RecordsReader> getRecordsReaders(ImportValueType importValueType);

  void persistNew(ExporterEntity... entitiesToPersist);

  void persistOperateEntitiesNew(List<? extends ExporterEntity> operateEntities)
      throws PersistenceException;

  Map<Class<? extends ExporterEntity>, String> getEntityToAliasMap();

  int getOpenScrollcontextSize();

  void setIndexPrefix(String indexPrefix);

  boolean indexExists(String index) throws IOException;
}
