/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.RecordsReader;
import org.junit.runner.Description;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface SearchTestRuleProvider {
  void failed(Throwable e, Description description);

  void starting(Description description);

  void finished(Description description);

  void assertMaxOpenScrollContexts(final int maxOpenScrollContexts);

  void refreshSearchIndices();

  void refreshZeebeIndices();

  void refreshOperateSearchIndices();

  void processAllRecordsAndWait(Integer maxWaitingRounds, Predicate<Object[]> predicate, Object... arguments);

  void processAllRecordsAndWait(Predicate<Object[]> predicate, Object... arguments);

  void processAllRecordsAndWait(Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments);

  void processAllRecordsAndWait(boolean runPostImport, Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments);

  void processRecordsWithTypeAndWait(ImportValueType importValueType, Predicate<Object[]> predicate, Object... arguments);

  void processRecordsWithTypeAndWait(ImportValueType importValueType, boolean runPostImport, Predicate<Object[]> predicate, Object... arguments);

  void processRecordsAndWaitFor(Collection<RecordsReader> readers, Integer maxWaitingRounds, boolean runPostImport,
      Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments);

  void runPostImportActions();

  boolean areIndicesCreatedAfterChecks(String indexPrefix, int minCountOfIndices,int maxChecks);

  List<RecordsReader> getRecordsReaders(ImportValueType importValueType);

  void persistNew(OperateEntity... entitiesToPersist);

  void persistOperateEntitiesNew(List<? extends OperateEntity> operateEntities) throws PersistenceException;

  Map<Class<? extends OperateEntity>, String> getEntityToAliasMap();

  int getOpenScrollcontextSize();

  void setIndexPrefix(String indexPrefix);

  boolean indexExists(String index) throws IOException;
}
