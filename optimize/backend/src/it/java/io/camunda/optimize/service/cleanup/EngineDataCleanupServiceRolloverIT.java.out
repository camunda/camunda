/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.cleanup;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
// import io.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
// import io.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
// import java.time.OffsetDateTime;
// import java.util.List;
// import lombok.SneakyThrows;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
//
// public class EngineDataCleanupServiceRolloverIT extends AbstractCleanupIT {
//
//   @BeforeEach
//   @AfterEach
//   public void beforeAndAfter() {
//     cleanUpEventIndices();
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCleanupServiceConfiguration()
//         .getProcessDataCleanupConfiguration()
//         .setEnabled(true);
//   }
//
//   @Test
//   @SneakyThrows
//   public void testCleanupModeAll_camundaEventData_afterRollover() {
//     // given
//     embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
//     getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
//
//     final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
//         deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
//     importAllEngineEntitiesFromScratch();
//
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getEventIndexRolloverConfiguration()
//         .setMaxIndexSizeGB(0);
//     embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();
//
//     final ProcessInstanceEngineDto instanceToGetCleanedUpImportedAfterRollover =
//         startNewInstanceWithEndTimeLessThanTtl(instancesToGetCleanedUp.get(0));
//     instancesToGetCleanedUp.add(instanceToGetCleanedUpImportedAfterRollover);
//
//     final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
//         startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));
//
//     importAllEngineEntitiesFromLastIndex();
//
//     // when
//     embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     assertNoProcessInstanceDataExists(instancesToGetCleanedUp);
//
// assertPersistedProcessInstanceDataComplete(unaffectedProcessInstanceForSameDefinition.getId());
//     assertThat(getCamundaActivityEvents())
//         .extracting(CamundaActivityEventDto::getProcessInstanceId)
//         .containsOnly(unaffectedProcessInstanceForSameDefinition.getId());
//     assertThat(getAllCamundaEventBusinessKeys())
//         .extracting(BusinessKeyDto::getProcessInstanceId)
//         .containsOnly(unaffectedProcessInstanceForSameDefinition.getId());
//     assertThat(databaseIntegrationTestExtension.getAllStoredVariableUpdateInstanceDtos())
//         .isNotEmpty()
//         .extracting(VariableUpdateInstanceDto::getProcessInstanceId)
//         .containsOnly(unaffectedProcessInstanceForSameDefinition.getId());
//   }
//
//   @Test
//   @SneakyThrows
//   public void testCleanupModeVariables_camundaEventData_afterRollover() {
//     // given
//     embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
//     getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);
//
//     final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
//         deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
//     importAllEngineEntitiesFromScratch();
//
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getEventIndexRolloverConfiguration()
//         .setMaxIndexSizeGB(0);
//     embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();
//
//     final ProcessInstanceEngineDto instanceToGetCleanedUpImportedAfterRollover =
//         startNewInstanceWithEndTimeLessThanTtl(instancesToGetCleanedUp.get(0));
//     instancesToGetCleanedUp.add(instanceToGetCleanedUpImportedAfterRollover);
//
//     final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
//         startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));
//
//     importAllEngineEntitiesFromLastIndex();
//
//     // when
//     embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     assertVariablesEmptyInProcessInstances(extractProcessInstanceIds(instancesToGetCleanedUp));
//
// assertPersistedProcessInstanceDataComplete(unaffectedProcessInstanceForSameDefinition.getId());
//     assertThat(databaseIntegrationTestExtension.getAllStoredVariableUpdateInstanceDtos())
//         .isNotEmpty()
//         .extracting(VariableUpdateInstanceDto::getProcessInstanceId)
//         .containsOnly(unaffectedProcessInstanceForSameDefinition.getId());
//   }
// }
