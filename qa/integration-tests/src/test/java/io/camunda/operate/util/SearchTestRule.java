/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.zeebe.ImportValueType;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SearchTestRule extends TestWatcher {

  protected static final Logger logger = LoggerFactory.getLogger(SearchTestRule.class);

  @Autowired
  protected SearchTestRuleProvider searchTestRuleProvider;

  @Autowired
  protected OperateProperties operateProperties;

  Consumer<OperateProperties> operatePropertiesCustomizer = operateProperties -> {};

  public SearchTestRule() {
  }

  public SearchTestRule(String indexPrefix) {
    searchTestRuleProvider.setIndexPrefix(indexPrefix);
  }

  public SearchTestRule(Consumer<OperateProperties> operatePropertiesCustomizer) {
    this.operatePropertiesCustomizer = operatePropertiesCustomizer;
  }

  @Override
  protected void failed(Throwable e, Description description) {
    super.failed(e, description);
    searchTestRuleProvider.failed(e, description);
  }

  @Override
  protected void starting(Description description) {
    operatePropertiesCustomizer.accept(operateProperties);
    searchTestRuleProvider.starting(description);
  }

  @Override
  protected void finished(Description description) {
      searchTestRuleProvider.finished(description);
  }

  public void refreshSerchIndexes() {
    searchTestRuleProvider.refreshSearchIndices();
  }

  public void refreshZeebeIndices() {
    searchTestRuleProvider.refreshZeebeIndices();
  }

  public void refreshOperateSearchIndices() {
    searchTestRuleProvider.refreshOperateSearchIndices();
  }

  public void processAllRecordsAndWait(Integer maxWaitingRounds, Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(maxWaitingRounds, predicate, arguments);
  }

  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(predicate, arguments);
  }
  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(predicate, supplier, arguments);
  }

  public void processAllRecordsAndWait(boolean runPostImport, Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(runPostImport, predicate, supplier, arguments);
  }

  public void processRecordsWithTypeAndWait(ImportValueType importValueType, Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processRecordsWithTypeAndWait(importValueType, predicate, arguments);
  }
  public void processRecordsWithTypeAndWait(ImportValueType importValueType, boolean runPostImport, Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processRecordsWithTypeAndWait(importValueType, runPostImport, predicate, arguments);
  }

  public void persistNew(OperateEntity... entitiesToPersist) {
    searchTestRuleProvider.persistNew(entitiesToPersist);
  }

  public void persistOperateEntitiesNew(List<? extends OperateEntity> operateEntities) throws PersistenceException {
    searchTestRuleProvider.persistOperateEntitiesNew(operateEntities);
  }

  public Map<Class<? extends OperateEntity>, String> getEntityToAliasMap(){
    return searchTestRuleProvider.getEntityToAliasMap();
  }

  public boolean indexExists(String index) throws IOException {
    return searchTestRuleProvider.indexExists(index);
  }
}
