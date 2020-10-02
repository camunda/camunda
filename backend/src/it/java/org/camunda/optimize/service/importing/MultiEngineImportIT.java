/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex.TIMESTAMP_OF_LAST_ENTITY;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getExternalTaskProcess;

public class MultiEngineImportIT extends AbstractMultiEngineIT {

  @Test
  public void allProcessDefinitionsAreImported() {
    // given
    addSecondEngineToConfiguration();
    deployAndStartProcessDefinitionForAllEngines();

    // when
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    List<ProcessDefinitionOptimizeDto> definitions = elasticSearchIntegrationTestExtension.getAllProcessDefinitions();

    // then
    assertThat(definitions)
      .extracting(ProcessDefinitionOptimizeDto::getKey)
      .containsExactlyInAnyOrder(PROCESS_KEY_1, PROCESS_KEY_2);
  }

  @Test
  public void allProcessDefinitionsAreImported_importDeactivatedForOneEngine() {
    // given
    addSecondEngineToConfiguration(false);
    deployAndStartProcessDefinitionForAllEngines();

    // when
    try {
      embeddedOptimizeExtension.startContinuousImportScheduling();
      embeddedOptimizeExtension.ensureImportSchedulerIsIdle(60);
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
      List<ProcessDefinitionOptimizeDto> definitions = elasticSearchIntegrationTestExtension.getAllProcessDefinitions();

      // then
      assertThat(definitions)
        .extracting(ProcessDefinitionOptimizeDto::getKey)
        .containsExactlyInAnyOrder(PROCESS_KEY_1);
    } finally {
      embeddedOptimizeExtension.stopEngineImportScheduling();
    }
  }

  @Test
  public void allProcessInstancesEventsAndVariablesAreImported() {
    // given
    addSecondEngineToConfiguration();
    deployAndStartProcessDefinitionForAllEngines();

    // when
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    List<ProcessInstanceDto> processInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

    // then
    assertProcessInstanceImportResults(processInstances, PROCESS_KEY_1, PROCESS_KEY_2);
  }

  @Test
  public void allProcessInstancesEventsAndVariablesAreImported_importDeactivatedForOneEngine() {
    // given
    addSecondEngineToConfiguration(false);
    deployAndStartProcessDefinitionForAllEngines();

    // when
    try {
      embeddedOptimizeExtension.startContinuousImportScheduling();
      embeddedOptimizeExtension.ensureImportSchedulerIsIdle(60);
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
      List<ProcessInstanceDto> processInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

      // then
      assertProcessInstanceImportResults(processInstances, PROCESS_KEY_1);
    } finally {
      embeddedOptimizeExtension.stopEngineImportScheduling();
    }
  }

  @Test
  public void allProcessInstancesEventAndVariablesAreImportedWithAuthentication() {
    // given
    secondaryEngineIntegrationExtension.addUser("admin", "admin");
    secondaryEngineIntegrationExtension.grantAllAuthorizations("admin");
    addSecureSecondEngineToConfiguration();
    embeddedOptimizeExtension.reloadConfiguration();
    deployAndStartProcessDefinitionForAllEngines();

    // when
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    List<ProcessInstanceDto> processInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

    // then
    assertProcessInstanceImportResults(processInstances, PROCESS_KEY_1, PROCESS_KEY_2);
  }

  @Test
  public void allTenantsAreImported() {
    // given
    final String firstTenantId = "tenantId1";
    final String tenantName = "My New Tenant";
    final String secondTenantId = "tenantId2";
    addSecondEngineToConfiguration();
    engineIntegrationExtension.createTenant(firstTenantId, tenantName);
    secondaryEngineIntegrationExtension.createTenant(firstTenantId, tenantName);
    secondaryEngineIntegrationExtension.createTenant(secondTenantId, tenantName);

    // when
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<TenantDto> tenants = elasticSearchIntegrationTestExtension.getAllTenants();
    assertThat(tenants)
      .extracting(TenantDto::getId, TenantDto::getEngine)
      .containsExactlyInAnyOrder(
        Tuple.tuple(firstTenantId, DEFAULT_ENGINE_ALIAS),
        Tuple.tuple(secondTenantId, SECOND_ENGINE_ALIAS)
      );
  }

  @Test
  public void afterRestartOfOptimizeRightImportIndexIsUsed() throws Exception {
    // given
    deployAllPossibleEngineDataForAllEngines();

    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(TIMESTAMP_BASED_IMPORT_INDEX_NAME);

    assertThat(searchResponse.getHits().getTotalHits().value).isEqualTo(28L);
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String timestampOfLastEntity = searchHit.getSourceAsMap().get(TIMESTAMP_OF_LAST_ENTITY).toString();
      OffsetDateTime timestamp = OffsetDateTime.parse(
        timestampOfLastEntity,
        embeddedOptimizeExtension.getDateTimeFormatter()
      );
      assertThat(timestamp).isAfter(OffsetDateTime.now().minusHours(1));
    }
  }

  private void deployAllPossibleEngineDataForAllEngines() {
    addSecondEngineToConfiguration();
    deployAndStartProcessDefinitionForAllEngines();
    // we need finished user tasks
    deployAndStartUserTaskProcessForAllEngines();
    finishAllUserTasksForAllEngines();
    // as well as running & suspended ones
    final List<ProcessInstanceEngineDto> processInstancesToSuspend = deployAndStartUserTaskProcessForAllEngines();
    engineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstancesToSuspend.get(0).getId());
    secondaryEngineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstancesToSuspend.get(1).getId());
    deployAndStartDecisionDefinitionForAllEngines();

    // add incident data
    ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(getExternalTaskProcess());
    engineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
    engineIntegrationExtension.completeExternalTasks(processInstanceEngineDto.getId());
    processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(getExternalTaskProcess());
    engineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());

    processInstanceEngineDto = secondaryEngineIntegrationExtension.deployAndStartProcess(getExternalTaskProcess());
    secondaryEngineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
    secondaryEngineIntegrationExtension.completeExternalTasks(processInstanceEngineDto.getId());
    processInstanceEngineDto = secondaryEngineIntegrationExtension.deployAndStartProcess(getExternalTaskProcess());
    secondaryEngineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
  }

  private void assertProcessInstanceImportResults(final List<ProcessInstanceDto> processInstances,
                                                  final String... expectedDefinitionKeys) {
    assertThat(processInstances)
      .allSatisfy(processInstance -> {
        assertThat(processInstance.getEvents()).hasSize(2);
        assertThat(processInstance.getVariables()).hasSize(1);
      })
      .extracting(ProcessInstanceDto::getProcessDefinitionKey)
      .containsExactlyInAnyOrder(expectedDefinitionKeys);
  }

}
