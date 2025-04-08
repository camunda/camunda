/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class SearchTestRule extends TestWatcher {

  protected static final Logger LOGGER = LoggerFactory.getLogger(SearchTestRule.class);

  @Autowired protected SearchTestRuleProvider searchTestRuleProvider;

  @Autowired protected OperateProperties operateProperties;

  Consumer<OperateProperties> operatePropertiesCustomizer = operateProperties -> {};

  public SearchTestRule() {}

  public SearchTestRule(final String indexPrefix) {
    searchTestRuleProvider.setIndexPrefix(indexPrefix);
  }

  public SearchTestRule(final Consumer<OperateProperties> operatePropertiesCustomizer) {
    this.operatePropertiesCustomizer = operatePropertiesCustomizer;
  }

  @Override
  protected void failed(final Throwable e, final Description description) {
    super.failed(e, description);
    searchTestRuleProvider.failed(e, description);
  }

  @Override
  protected void starting(final Description description) {
    operatePropertiesCustomizer.accept(operateProperties);
    searchTestRuleProvider.starting(description);
  }

  @Override
  protected void finished(final Description description) {
    searchTestRuleProvider.finished(description);
  }

  public void refreshSerchIndexes() {
    searchTestRuleProvider.refreshSearchIndices();
  }

  public void refreshOperateSearchIndices() {
    searchTestRuleProvider.refreshOperateSearchIndices();
  }

  public void processAllRecordsAndWait(
      final Integer maxWaitingRounds,
      final Predicate<Object[]> predicate,
      final Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(maxWaitingRounds, predicate, arguments);
  }

  public void processAllRecordsAndWait(
      final Predicate<Object[]> predicate, final Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(predicate, arguments);
  }

  public void processAllRecordsAndWait(
      final Predicate<Object[]> predicate,
      final Supplier<Object> supplier,
      final Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(predicate, supplier, arguments);
  }

  public void processRecordsWithTypeAndWait(
      final ImportValueType importValueType,
      final Predicate<Object[]> predicate,
      final Object... arguments) {
    searchTestRuleProvider.processRecordsWithTypeAndWait(importValueType, predicate, arguments);
  }

  public void persistNew(final ExporterEntity... entitiesToPersist) {
    searchTestRuleProvider.persistNew(entitiesToPersist);
  }

  public void persistOperateEntitiesNew(final List<? extends ExporterEntity> operateEntities)
      throws PersistenceException {
    searchTestRuleProvider.persistOperateEntitiesNew(operateEntities);
  }

  public Map<Class<? extends ExporterEntity>, String> getEntityToAliasMap() {
    return searchTestRuleProvider.getEntityToAliasMap();
  }

  public boolean indexExists(final String index) throws IOException {
    return searchTestRuleProvider.indexExists(index);
  }
}
