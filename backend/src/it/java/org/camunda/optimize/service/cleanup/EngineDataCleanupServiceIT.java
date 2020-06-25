/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.SneakyThrows;
import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EngineDataCleanupServiceIT extends AbstractEngineDataCleanupIT {

  @Test
  @SneakyThrows
  public void testCleanupModeAll() {
    // given
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertNoInstanceDataExists(instancesToGetCleanedUp);
    assertProcessInstanceDataCompleteInEs(unaffectedProcessInstanceForSameDefinition.getId());
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll_disabled() {
    // given
    getCleanupConfiguration().setEnabled(false);
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> unaffectedProcessInstances =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(unaffectedProcessInstances));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll_specificKey() {
    // given
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();

    final List<ProcessInstanceEngineDto> instancesOfDefinitionWithHigherTtl =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    configureHigherProcessSpecificTtl(instancesOfDefinitionWithHigherTtl.get(0).getProcessDefinitionKey());

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertNoInstanceDataExists(instancesToGetCleanedUp);
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll_camundaEventData() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertNoInstanceDataExists(instancesToGetCleanedUp);
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
  public void testCleanupModeAll_camundaEventData_specificKey() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();

    final List<ProcessInstanceEngineDto> instancesOfDefinitionWithHigherTtl =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    configureHigherProcessSpecificTtl(instancesOfDefinitionWithHigherTtl.get(0).getProcessDefinitionKey());

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertNoInstanceDataExists(instancesToGetCleanedUp);
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
    assertThat(getCamundaActivityEvents())
      .extracting(CamundaActivityEventDto::getProcessInstanceId)
      .containsAnyElementsOf(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
    assertThat(getAllCamundaEventBusinessKeys())
      .extracting(BusinessKeyDto::getProcessInstanceId)
      .containsAnyElementsOf(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredVariableUpdateInstanceDtos())
      .isNotEmpty()
      .extracting(VariableUpdateInstanceDto::getProcessInstanceId)
      .containsAnyElementsOf(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables() {
    // given
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertVariablesEmptyInProcessInstances(extractProcessInstanceIds(instancesToGetCleanedUp));
    assertProcessInstanceDataCompleteInEs(unaffectedProcessInstanceForSameDefinition.getId());
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_specificKey() {
    // given
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();

    final List<ProcessInstanceEngineDto> instancesOfDefinitionWithHigherTtl =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    configureHigherProcessSpecificTtl(instancesOfDefinitionWithHigherTtl.get(0).getProcessDefinitionKey());

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertVariablesEmptyInProcessInstances(extractProcessInstanceIds(instancesToGetCleanedUp));
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_camundaEventData() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);

    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromScratch();

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

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_camundaEventData_specificKey() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);

    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();

    final List<ProcessInstanceEngineDto> instancesOfDefinitionWithHigherTtl =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    configureHigherProcessSpecificTtl(instancesOfDefinitionWithHigherTtl.get(0).getProcessDefinitionKey());

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertVariablesEmptyInProcessInstances(extractProcessInstanceIds(instancesToGetCleanedUp));
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredVariableUpdateInstanceDtos())
      .isNotEmpty()
      .extracting(VariableUpdateInstanceDto::getProcessInstanceId)
      .containsAnyElementsOf(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
  }

  @Test
  @SneakyThrows
  @SuppressWarnings("unchecked")
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingProcessDefinitionNoInstancesCleaned() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      configuredKey,
      new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES)
    );
    // and deploy processes with different keys
    final List<ProcessInstanceEngineDto> instancesWithEndTimeLessThanTtl =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final List<ProcessInstanceEngineDto> instancesWithEndTimeWithinTtl =
      deployProcessAndStartTwoProcessInstancesWithEndTime(OffsetDateTime.now());

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // all data is still there
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(
      (List<ProcessInstanceEngineDto>) ListUtils.union(instancesWithEndTimeLessThanTtl, instancesWithEndTimeWithinTtl)
    ));
  }

  @Test
  @SneakyThrows
  public void testCleanupWithDecisionInstanceDelete() {
    // given
    deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertDecisionInstancesExistInEs(Collections.emptyList());
  }

  @Test
  @SneakyThrows
  public void testCleanupWithDecisionInstanceDeleteVerifyThatNewOnesAreUnaffected() {
    // given
    deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
    final List<String> unaffectedDecisionDefinitionsIds = deployTwoDecisionInstancesWithEvaluationTime(
      OffsetDateTime.now()
    );

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    assertDecisionInstancesExistInEs(unaffectedDecisionDefinitionsIds);
  }

  @Test
  @SneakyThrows
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingDecisionDefinitionNoInstancesCleaned() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration().getDecisionDefinitionSpecificConfiguration().put(
      configuredKey,
      new DecisionDefinitionCleanupConfiguration(getCleanupConfiguration().getDefaultTtl())
    );
    // and deploy processes with different keys
    final List<String> decisionDefinitionsWithEvaluationTimeLessThanTtl =
      deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
    final List<String> unaffectedDecisionDefinitionsIds =
      deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime.now());

    importAllEngineEntitiesFromScratch();

    //when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // all data is still there
    assertDecisionInstancesExistInEs(
      ListUtils.union(decisionDefinitionsWithEvaluationTimeLessThanTtl, unaffectedDecisionDefinitionsIds)
    );
  }

}
