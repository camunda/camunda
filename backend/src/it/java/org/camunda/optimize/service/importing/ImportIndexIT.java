/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
    deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();

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
    startAndUseNewOptimizeInstance();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(embeddedOptimizeExtension.getImportIndexes())
      .allSatisfy(index -> assertThat(index).isPositive());
  }

  @Test
  public void indexAfterRestartOfOptimizeHasCorrectProcessDefinitionsToImport() {
    // given
    deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
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
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();
    List<Long> firstRoundIndexes = embeddedOptimizeExtension.getImportIndexes();

    // and
    deployAndStartSimpleServiceTaskProcess();

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
    deployAndStartSimpleServiceTaskProcess();
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
    deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.createTenant("id", "name");

    // create incident data
    ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(getExternalTaskProcess());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
    incidentClient.resolveOpenIncidents(processInstanceEngineDto.getId());
    processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(getExternalTaskProcess());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
  }
}
