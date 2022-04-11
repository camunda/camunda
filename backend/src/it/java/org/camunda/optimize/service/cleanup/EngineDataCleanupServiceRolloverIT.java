/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EngineDataCleanupServiceRolloverIT extends AbstractCleanupIT {

  @BeforeEach
  @AfterEach
  public void beforeAndAfter() {
    cleanUpEventIndices();
    embeddedOptimizeExtension.getConfigurationService()
      .getCleanupServiceConfiguration()
      .getProcessDataCleanupConfiguration()
      .setEnabled(true);
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll_camundaEventData_afterRollover() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);

    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();

    final ProcessInstanceEngineDto instanceToGetCleanedUpImportedAfterRollover =
      startNewInstanceWithEndTimeLessThanTtl(instancesToGetCleanedUp.get(0));
    instancesToGetCleanedUp.add(instanceToGetCleanedUpImportedAfterRollover);

    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromLastIndex();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);
    assertProcessInstanceDataCompleteInEs(unaffectedProcessInstanceForSameDefinition.getId());
    assertThat(getCamundaActivityEvents())
      .extracting(CamundaActivityEventDto::getProcessInstanceId)
      .containsOnly(unaffectedProcessInstanceForSameDefinition.getId());
    assertThat(getAllCamundaEventBusinessKeys())
      .extracting(BusinessKeyDto::getProcessInstanceId)
      .containsOnly(unaffectedProcessInstanceForSameDefinition.getId());
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredVariableUpdateInstanceDtos())
      .isNotEmpty()
      .extracting(VariableUpdateInstanceDto::getProcessInstanceId)
      .containsOnly(unaffectedProcessInstanceForSameDefinition.getId());
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_camundaEventData_afterRollover() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);

    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();

    final ProcessInstanceEngineDto instanceToGetCleanedUpImportedAfterRollover =
      startNewInstanceWithEndTimeLessThanTtl(instancesToGetCleanedUp.get(0));
    instancesToGetCleanedUp.add(instanceToGetCleanedUpImportedAfterRollover);

    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromLastIndex();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertVariablesEmptyInProcessInstances(extractProcessInstanceIds(instancesToGetCleanedUp));
    assertProcessInstanceDataCompleteInEs(unaffectedProcessInstanceForSameDefinition.getId());
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredVariableUpdateInstanceDtos())
      .isNotEmpty()
      .extracting(VariableUpdateInstanceDto::getProcessInstanceId)
      .containsOnly(unaffectedProcessInstanceForSameDefinition.getId());
  }

}
