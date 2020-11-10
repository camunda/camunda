/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.SneakyThrows;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getExternalTaskProcess;

public class ImportIndexIT extends AbstractImportIT {

  @Test
  public void importIndexIsZeroIfNothingIsImportedYet() {
    // when
    List<Long> indexes = embeddedOptimizeExtension.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index).isZero();
    }
  }

  @Test
  public void indexLastTimestampIsEqualEvenAfterReset() throws InterruptedException {
    // given
    final int currentTimeBackOff = 1000;
    embeddedOptimizeExtension.getConfigurationService().setCurrentTimeBackoffMilliseconds(currentTimeBackOff);
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();

    // sleep in order to avoid the timestamp import backoff window that modifies the latestTimestamp stored
    Thread.sleep(currentTimeBackOff);

    importAllEngineEntitiesFromScratch();
    List<Long> firstRoundIndexes = embeddedOptimizeExtension.getImportIndexes();

    // then
    embeddedOptimizeExtension.resetImportStartIndexes();
    importAllEngineEntitiesFromScratch();
    List<Long> secondsRoundIndexes = embeddedOptimizeExtension.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i)).isEqualTo(secondsRoundIndexes.get(i));
    }
  }

  @Test
  @SneakyThrows
  public void latestImportIndexAfterRestartOfOptimize() {
    // given
    deployAllPossibleEngineData();

    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    List<Long> indexes = embeddedOptimizeExtension.getImportIndexes();
    for (Long index : indexes) {
      assertThat(index).isGreaterThan(0L);
    }
  }

  @Test
  public void indexAfterRestartOfOptimizeHasCorrectProcessDefinitionsToImport() {
    // given
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeExtension.getImportIndexes();

    // when
    importAllEngineEntitiesFromScratch();
    List<Long> secondsRoundIndexes = embeddedOptimizeExtension.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i)).isEqualTo(secondsRoundIndexes.get(i));
    }
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() {
    // given
    deployAndStartSimpleServiceTask();
    importAllEngineEntitiesFromScratch();
    List<Long> firstRoundIndexes = embeddedOptimizeExtension.getImportIndexes();

    // and
    deployAndStartSimpleServiceTask();

    // when
    importAllEngineEntitiesFromScratch();
    List<Long> secondsRoundIndexes = embeddedOptimizeExtension.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i)).isLessThanOrEqualTo(secondsRoundIndexes.get(i));
    }
  }

  @Test
  public void itIsPossibleToResetTheImportIndex() {
    // given
    deployAndStartSimpleServiceTask();
    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.resetImportStartIndexes();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<Long> indexes = embeddedOptimizeExtension.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index).isZero();
    }
  }

  private void deployAllPossibleEngineData() {
    deployAndStartUserTaskProcess();
    // we need finished ones
    engineIntegrationExtension.finishAllRunningUserTasks();
    // as well as running & suspended ones
    final ProcessInstanceEngineDto processInstanceToSuspend = deployAndStartUserTaskProcess();
    engineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstanceToSuspend.getId());
    deployAndStartSimpleServiceTask();
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.createTenant("id", "name");

    // create incident data
    ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(getExternalTaskProcess());
    engineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
    engineIntegrationExtension.completeExternalTasks(processInstanceEngineDto.getId());
    processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(getExternalTaskProcess());
    engineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
  }
}
