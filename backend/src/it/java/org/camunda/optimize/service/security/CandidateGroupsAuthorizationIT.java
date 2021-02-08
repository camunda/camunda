/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getUserTaskDiagramWithCandidateGroup;

public class CandidateGroupsAuthorizationIT extends AbstractIT {

  @Test
  public void getAssigneesForUnauthorizedTenant() {
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      TENANT_INDEX_NAME,
      "newTenant",
      new TenantDto("newTenant", "newTenant", DEFAULT_ENGINE_ALIAS)
    );

    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "aProcess",
      Collections.singletonList("ALL"),
      Collections.singletonList("newTenant")
    );

    importAllEngineEntitiesFromScratch();

    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetCandidateGroupsRequest(requestDto)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getCandidateGroupsWithoutAuthentication() {
    startSimpleUserTaskProcessWithCandidateGroup();

    importAllEngineEntitiesFromScratch();

    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "aProcess",
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );

    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetCandidateGroupsRequest(requestDto)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void searchForCandidateGroups_forReports_missingCollectionAuth() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    engineIntegrationExtension.createGroup("candidateGroupId", "candidateGroupName");
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcess(getUserTaskDiagramWithCandidateGroup("candidateGroupId"));
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      instance.getProcessDefinitionKey(),
      DefinitionType.PROCESS,
      Collections.singletonList(null)
    );
    final String reportId = reportClient.createAndStoreProcessReport(collectionId, instance.getProcessDefinitionKey());
    importAllEngineEntitiesFromScratch();

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroupsAsUser(
      KERMIT_USER,
      KERMIT_USER,
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isZero();
    assertThat(searchResponse.getResult()).isEmpty();
  }

  @Test
  public void searchForCandidateGroups_forReports_missingReportDefinitionAuth() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    engineIntegrationExtension.createGroup("candidateGroupId", "candidateGroupName");
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcess(getUserTaskDiagramWithCandidateGroup("candidateGroupId"));
    final String reportId = reportClient.createSingleProcessReportAsUser(
      instance.getProcessDefinitionKey(),
      KERMIT_USER,
      KERMIT_USER
    );
    importAllEngineEntitiesFromScratch();

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroupsAsUser(
      KERMIT_USER,
      KERMIT_USER,
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isZero();
    assertThat(searchResponse.getResult()).isEmpty();
  }

  @Test
  public void searchForCandidateGroups_forReports_partialReportDefinitionAuth() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      "authorizedDef",
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    engineIntegrationExtension.createGroup("candidateGroupId", "candidateGroupName");
    engineIntegrationExtension.createGroup("otherCandidateGroupId", "otherCandidateGroupName");

    final ProcessInstanceEngineDto authDefInstance =
      engineIntegrationExtension.deployAndStartProcess(getUserTaskDiagramWithCandidateGroup(
        "authorizedDef",
        "candidateGroupId"
      ));
    final ProcessInstanceEngineDto noAuthDefInstance =
      engineIntegrationExtension.deployAndStartProcess(getUserTaskDiagramWithCandidateGroup(
        "unauthorizedDef",
        "otherCandidateGroupId"
      ));

    final String authorizedReportId = reportClient.createSingleProcessReportAsUser(
      authDefInstance.getProcessDefinitionKey(),
      KERMIT_USER,
      KERMIT_USER
    );
    final String unauthorizedReportId = reportClient.createSingleProcessReportAsUser(
      noAuthDefInstance.getProcessDefinitionKey(),
      KERMIT_USER,
      KERMIT_USER
    );

    importAllEngineEntitiesFromScratch();

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroupsAsUser(
      KERMIT_USER,
      KERMIT_USER,
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(authorizedReportId, unauthorizedReportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto("candidateGroupId", "candidateGroupName")
      );
  }

  private void startSimpleUserTaskProcessWithCandidateGroup() {
    engineIntegrationExtension.deployAndStartProcess(
      Bpmn.createExecutableProcess("aProcess")
        .startEvent()
        .userTask().camundaCandidateGroups("marketing")
        .userTask().camundaCandidateGroups("sales")
        .endEvent()
        .done()
    );
  }

}
