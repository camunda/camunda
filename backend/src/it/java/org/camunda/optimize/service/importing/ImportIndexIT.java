/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class ImportIndexIT extends AbstractImportIT {

  @Test
  public void importIndexIsZeroIfNothingIsImportedYet() {
    // when
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, is(0L));
    }
  }

  @Test
  public void indexLastTimestampIsEqualEvenAfterReset() throws InterruptedException {
    // given
    final int currentTimeBackOff = 1000;
    embeddedOptimizeRule.getConfigurationService().setCurrentTimeBackoffMilliseconds(currentTimeBackOff);
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();

    // sleep in order to avoid the timestamp import backoff window that modifies the latestTimestamp stored
    Thread.sleep(currentTimeBackOff);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    embeddedOptimizeRule.resetImportStartIndexes();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> secondsRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), is(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void latestImportIndexAfterRestartOfOptimize() throws Exception {
    // given
    deployAndStartUserTaskProcess();
    // we need finished ones
    engineRule.finishAllRunningUserTasks();
    // as well as running
    deployAndStartUserTaskProcess();
    deployAndStartSimpleServiceTask();
    engineRule.deployAndStartDecisionDefinition();
    engineRule.createTenant("id", "name");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, greaterThan(0L));
    }
  }

  @Test
  public void indexAfterRestartOfOptimizeHasCorrectProcessDefinitionsToImport() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Long> secondsRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), is(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // and
    deployAndStartSimpleServiceTask();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> secondsRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), lessThanOrEqualTo(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void itIsPossibleToResetTheImportIndex() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeRule.resetImportStartIndexes();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, is(0L));
    }
  }
}
