/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.SneakyThrows;
import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static org.mockserver.model.JsonBody.json;

public class EngineDataProcessCleanupServiceIT extends AbstractCleanupIT {

  @BeforeEach
  public void enableProcessCleanup() {
    embeddedOptimizeExtension.getConfigurationService()
      .getCleanupServiceConfiguration()
      .getProcessDataCleanupConfiguration()
      .setEnabled(true);
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll() {
    // given
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);
    assertProcessInstanceDataCompleteInEs(unaffectedProcessInstanceForSameDefinition.getId());
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll_disabled() {
    // given
    getProcessDataCleanupConfiguration().setEnabled(false);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> unaffectedProcessInstances =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(unaffectedProcessInstances));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll_customBatchSize() {
    // given
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    getProcessDataCleanupConfiguration().setBatchSize(1);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromScratch();

    final ClientAndServer elasticsearchFacade = useAndGetElasticsearchMockServer();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);
    assertProcessInstanceDataCompleteInEs(unaffectedProcessInstanceForSameDefinition.getId());
    instancesToGetCleanedUp.forEach(instance -> elasticsearchFacade.verify(
      HttpRequest.request()
        .withPath("/_bulk")
        .withBody(json(createBulkDeleteProcessInstanceRequestJson(
          instance.getId(),
          instance.getProcessDefinitionKey()
        ))),
      VerificationTimes.exactly(1)
    ));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll_specificKeyTtl() {
    // given
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
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
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(instancesOfDefinitionWithHigherTtl));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeAll_camundaEventData() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromScratch();

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
  public void testCleanupModeAll_camundaEventData_specificKeyTtl() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
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
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);
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
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);
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
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_customBatchSize() {
    // given
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);
    getProcessDataCleanupConfiguration().setBatchSize(1);
    final List<ProcessInstanceEngineDto> instancesToGetCleanedUp =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final ProcessInstanceEngineDto unaffectedProcessInstanceForSameDefinition =
      startNewInstanceWithEndTime(OffsetDateTime.now(), instancesToGetCleanedUp.get(0));

    importAllEngineEntitiesFromScratch();

    final ClientAndServer elasticsearchFacade = useAndGetElasticsearchMockServer();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertVariablesEmptyInProcessInstances(extractProcessInstanceIds(instancesToGetCleanedUp));
    assertProcessInstanceDataCompleteInEs(unaffectedProcessInstanceForSameDefinition.getId());
    instancesToGetCleanedUp.forEach(instance -> elasticsearchFacade.verify(
      HttpRequest.request()
        .withPath("/_bulk")
        .withBody(json(createBulkUpdateProcessInstanceRequestJson(
          instance.getId(),
          instance.getProcessDefinitionKey()
        ))),
      VerificationTimes.exactly(1)
    ));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_specificKeyCleanupMode() {
    // given
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    final List<ProcessInstanceEngineDto> instancesOfDefinitionWithVariableMode =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    getCleanupConfiguration().getProcessDataCleanupConfiguration()
      .getProcessDefinitionSpecificConfiguration()
      .put(
        instancesOfDefinitionWithVariableMode.get(0).getProcessDefinitionKey(),
        ProcessDefinitionCleanupConfiguration.builder().cleanupMode(CleanupMode.VARIABLES).build()
      );

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertVariablesEmptyInProcessInstances(extractProcessInstanceIds(instancesOfDefinitionWithVariableMode));
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_specificKeyTtl() {
    // given
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);
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
  }

  @Test
  @SneakyThrows
  public void testCleanupModeVariables_camundaEventData() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);

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
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);

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
  @SuppressWarnings(UNCHECKED_CAST)
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingProcessDefinitionNoInstancesCleaned() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getProcessDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      configuredKey,
      new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES)
    );
    // and deploy processes with different keys
    final List<ProcessInstanceEngineDto> instancesWithEndTimeLessThanTtl =
      deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl();
    final List<ProcessInstanceEngineDto> instancesWithEndTimeWithinTtl =
      deployProcessAndStartTwoProcessInstancesWithEndTime(OffsetDateTime.now());

    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // all data is still there
    assertProcessInstanceDataCompleteInEs(extractProcessInstanceIds(
      (List<ProcessInstanceEngineDto>) ListUtils.union(instancesWithEndTimeLessThanTtl, instancesWithEndTimeWithinTtl)
    ));
  }

  private String createBulkDeleteProcessInstanceRequestJson(final String processInstanceId,
                                                            final String processDefinitionKey) {
    return createBulkProcessInstanceRequestJson(processInstanceId, processDefinitionKey, "delete");
  }

  private String createBulkUpdateProcessInstanceRequestJson(final String processInstanceId,
                                                            final String processDefinitionKey) {
    return createBulkProcessInstanceRequestJson(processInstanceId, processDefinitionKey, "update");
  }

  private String createBulkProcessInstanceRequestJson(final String processInstanceId, final String processDefinitionKey,
                                                      final String operation) {
    return String.format(
      "{\"%s\":{\"_index\":\"%s\",\"_id\":\"%s\"}}",
      operation,
      embeddedOptimizeExtension.getOptimizeElasticClient().getIndexNameService()
        .getOptimizeIndexAliasForIndex(getProcessInstanceIndexAliasName(processDefinitionKey)),
      processInstanceId
    );
  }

}
