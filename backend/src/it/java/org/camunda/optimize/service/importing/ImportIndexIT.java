/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class ImportIndexIT extends AbstractImportIT {

  @Test
  public void importIndexIsZeroIfNothingIsImportedYet() {
    // when
    List<Long> indexes = embeddedOptimizeExtensionRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, is(0L));
    }
  }

  @Test
  public void indexLastTimestampIsEqualEvenAfterReset() throws InterruptedException {
    // given
    final int currentTimeBackOff = 1000;
    embeddedOptimizeExtensionRule.getConfigurationService().setCurrentTimeBackoffMilliseconds(currentTimeBackOff);
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();

    // sleep in order to avoid the timestamp import backoff window that modifies the latestTimestamp stored
    Thread.sleep(currentTimeBackOff);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeExtensionRule.getImportIndexes();

    // then
    embeddedOptimizeExtensionRule.resetImportStartIndexes();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    List<Long> secondsRoundIndexes = embeddedOptimizeExtensionRule.getImportIndexes();

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
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    // as well as running
    deployAndStartUserTaskProcess();
    deployAndStartSimpleServiceTask();
    engineIntegrationExtensionRule.deployAndStartDecisionDefinition();
    engineIntegrationExtensionRule.createTenant("id", "name");

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtensionRule.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtensionRule.stopOptimize();
    embeddedOptimizeExtensionRule.startOptimize();
    List<Long> indexes = embeddedOptimizeExtensionRule.getImportIndexes();

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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtensionRule.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeExtensionRule.getImportIndexes();

    // when
    embeddedOptimizeExtensionRule.stopOptimize();
    embeddedOptimizeExtensionRule.startOptimize();
    List<Long> secondsRoundIndexes = embeddedOptimizeExtensionRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), is(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeExtensionRule.getImportIndexes();

    // and
    deployAndStartSimpleServiceTask();

    // when
    embeddedOptimizeExtensionRule.stopOptimize();
    embeddedOptimizeExtensionRule.startOptimize();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    List<Long> secondsRoundIndexes = embeddedOptimizeExtensionRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), lessThanOrEqualTo(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void itIsPossibleToResetTheImportIndex() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtensionRule.resetImportStartIndexes();
    embeddedOptimizeExtensionRule.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    embeddedOptimizeExtensionRule.stopOptimize();
    embeddedOptimizeExtensionRule.startOptimize();
    List<Long> indexes = embeddedOptimizeExtensionRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, is(0L));
    }
  }
}
