/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupDefinitionSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateGroupsRestServiceIT extends AbstractIT {

  private static final String CANDIDATE_GROUP_ID_IMPOSTERS = "imposters";
  private static final String CANDIDATE_GROUP_NAME_IMPOSTERS = "The Evil Imposters";

  private static final String CANDIDATE_GROUP_ID_CREW_MEMBERS = "crewMembers";
  private static final String CANDIDATE_GROUP_NAME_CREW_MEMBERS = "The Crew Members";

  @Test
  public void getCandidateGroupById() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);

    // when
    final List<GroupDto> candidateGroups = candidateGroupClient
      .getCandidateGroupsByIdsWithoutAuthentication(ImmutableList.of(CANDIDATE_GROUP_ID_IMPOSTERS));

    // then
    assertThat(candidateGroups)
      .singleElement()
      .extracting(GroupDto::getName)
      .isEqualTo(CANDIDATE_GROUP_NAME_IMPOSTERS);
  }

  @Test
  public void getCandidateGroupByIdNotExistingReflectsIdAsNameBack() {
    // when
    final List<GroupDto> candidateGroups =
      candidateGroupClient.getCandidateGroupsByIdsWithoutAuthentication(ImmutableList.of(CANDIDATE_GROUP_ID_IMPOSTERS));

    // then
    assertThat(candidateGroups)
      .singleElement()
      .extracting(GroupDto::getName)
      .isEqualTo(CANDIDATE_GROUP_ID_IMPOSTERS);
  }

  @Test
  public void getCandidateGroupsByIds() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final List<GroupDto> candidateGroups = candidateGroupClient.getCandidateGroupsByIdsWithoutAuthentication(
      ImmutableList.of(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_ID_CREW_MEMBERS)
    );

    // then
    assertThat(candidateGroups)
      .hasSize(2)
      .extracting(GroupDto::getName)
      .containsExactlyInAnyOrder(CANDIDATE_GROUP_NAME_IMPOSTERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
  }

  @Test
  public void getCandidateGroupByIdsOneNotExisting() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);

    // when
    final List<GroupDto> candidateGroups = candidateGroupClient.getCandidateGroupsByIdsWithoutAuthentication(
      ImmutableList.of(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_ID_CREW_MEMBERS)
    );

    // then
    assertThat(candidateGroups)
      .hasSize(2)
      .extracting(GroupDto::getName)
      .containsExactlyInAnyOrder(CANDIDATE_GROUP_NAME_IMPOSTERS, CANDIDATE_GROUP_ID_CREW_MEMBERS);
  }

  @Test
  public void searchForCandidateGroups_forDefinition_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForCandidateGroupsRequest(AssigneeCandidateGroupDefinitionSearchRequestDto.builder().build())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void searchForCandidateGroups_forReports_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForCandidateGroupsRequest(AssigneeCandidateGroupReportSearchRequestDto.builder().build())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void searchForCandidateGroups_missingKeyIsRejected() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForCandidateGroupsRequest(AssigneeCandidateGroupDefinitionSearchRequestDto.builder().build())
      .execute();

    // then the status code is bad request
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void searchForCandidateGroups_missingReportIdsIsRejected() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForCandidateGroupsRequest(AssigneeCandidateGroupReportSearchRequestDto.builder().build())
      .execute();

    // then the status code is bad request
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void searchForCandidateGroupsNoSearchTerm_forDefinition() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
    final String candidateGroupNotInEngine = "anotherGroupID";
    startSimpleUserTaskProcessWithCandidateGroupAndImport(candidateGroupNotInEngine);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(3);
    assertThat(searchResponse.getResult())
      .hasSize(3)
      .containsExactlyInAnyOrder(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS),
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS),
        new GroupDto(candidateGroupNotInEngine, candidateGroupNotInEngine)
      );
  }

  @Test
  public void searchForCandidateGroupsNoSearchTerm_forReports() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
    final String candidateGroupNotInEngine = "anotherGroupID";
    startSimpleUserTaskProcessWithCandidateGroupAndImport(candidateGroupNotInEngine);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(3);
    assertThat(searchResponse.getResult())
      .hasSize(3)
      .containsExactlyInAnyOrder(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS),
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS),
        new GroupDto(candidateGroupNotInEngine, candidateGroupNotInEngine)
      );
  }

  @Test
  public void searchForCandidateGroupsNoSearchTerm_forReportsWithMultipleDefinitions() {
    // given
    final String key1 = "key1";
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(key1, CANDIDATE_GROUP_ID_IMPOSTERS);
    final String key2 = "key2";
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(key2, CANDIDATE_GROUP_ID_CREW_MEMBERS);

    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .definitions(List.of(new ReportDataDefinitionDto(key1), new ReportDataDefinitionDto(key2)))
      .build();
    final String reportId = reportClient.createSingleProcessReport(reportDataDto);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS),
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS)
      );
  }

  @Test
  public void searchForCandidateGroupsNoSearchTerm_otherProcessCandidateGroupIsFiltered() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final String definitionKey1 = "process";
    startSimpleUserTaskProcessWithCandidateGroupAndImport(definitionKey1, CANDIDATE_GROUP_ID_IMPOSTERS);
    final String definitionKey2 = "otherProcess";
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(definitionKey2, CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(definitionKey1)
        .build()
    );
    final IdentitySearchResultResponseDto otherDefinitionSearchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(definitionKey2)
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );

    assertThat(otherDefinitionSearchResponse.getTotal()).isEqualTo(1);
    assertThat(otherDefinitionSearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS)
      );
  }

  @Test
  public void searchForCandidateGroupsNoSearchTerm_otherReportCandidateGroupIsFiltered() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final String definitionKey1 = "process";
    startSimpleUserTaskProcessWithCandidateGroupAndImport(definitionKey1, CANDIDATE_GROUP_ID_IMPOSTERS);
    final String definitionKey2 = "otherProcess";
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(definitionKey2, CANDIDATE_GROUP_ID_CREW_MEMBERS);

    final String reportId1 = reportClient.createAndStoreProcessReport(definitionKey1);
    final String reportId2 = reportClient.createAndStoreProcessReport(definitionKey2);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId1))
        .build()
    );
    final IdentitySearchResultResponseDto otherReportSearchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId2))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );

    assertThat(otherReportSearchResponse.getTotal()).isEqualTo(1);
    assertThat(otherReportSearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS)
      );
  }

  @Test
  public void searchForCandidateGroupsNoSearchTerm_forDefinition_tenantAssigneeIsFiltered() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(
      processInstanceEngineDto.getProcessDefinitionKey(), CANDIDATE_GROUP_ID_CREW_MEMBERS, tenant1
    );

    // when
    final IdentitySearchResultResponseDto noTenantSearchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .build()
    );
    final IdentitySearchResultResponseDto tenant1SearchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .tenantIds(ImmutableList.of(tenant1))
        .build()
    );

    // then
    assertThat(noTenantSearchResponse.getTotal()).isEqualTo(1);
    assertThat(noTenantSearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );

    assertThat(tenant1SearchResponse.getTotal()).isEqualTo(1);
    assertThat(tenant1SearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS)
      );
  }

  @Test
  public void searchForCandidateGroupsNoSearchTerm_forReports_tenantAssigneeIsFiltered() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(
      instance.getProcessDefinitionKey(), CANDIDATE_GROUP_ID_CREW_MEMBERS, tenant1
    );
    final String reportId1 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String reportId2 = reportClient.createAndStoreProcessReport(
      instance.getProcessDefinitionKey(),
      Collections.singletonList(tenant1)
    );

    // when
    final IdentitySearchResultResponseDto noTenantSearchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId1))
        .build()
    );
    final IdentitySearchResultResponseDto tenant1SearchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId2))
        .build()
    );

    // then
    assertThat(noTenantSearchResponse.getTotal()).isEqualTo(1);
    assertThat(noTenantSearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );

    assertThat(tenant1SearchResponse.getTotal()).isEqualTo(1);
    assertThat(tenant1SearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS)
      );
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_forDefinition() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);


    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .terms("Imposters")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .hasSize(1)
      .containsExactlyInAnyOrder(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_forDefinition_groupNotInEngine() {
    // given
    final String candidateGroupNotInEngine = "anotherGroupID";
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(candidateGroupNotInEngine);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);


    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .terms("groupID")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .hasSize(1)
      .containsExactlyInAnyOrder(
        new GroupDto(candidateGroupNotInEngine, candidateGroupNotInEngine)
      );
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_forReports() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .terms("Imposters")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_forReports_groupNotInEngine() {
    // given
    final String candidateGroupNotInEngine = "anotherGroupID";
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(candidateGroupNotInEngine);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .terms("groupID")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(candidateGroupNotInEngine, candidateGroupNotInEngine)
      );
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_forDefinition_multipleHits() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .terms("The")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS),
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS)
      );
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_forReports_multipleHits() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .terms("The")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS),
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS)
      );
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_forDefinition_multipleHitsLimited() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .terms("The")
        .limit(1)
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult()).hasSize(1);
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_forReport_multipleHitsLimited() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .terms("The")
        .limit(1)
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult()).hasSize(1);
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_otherProcessCandidateGroupIsFiltered() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport("otherProcess", CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .terms("The")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );
  }

  @Test
  public void searchForCandidateGroupsWithSearchTerm_otherReportCandidateGroupIsFiltered() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport("otherProcess", CANDIDATE_GROUP_ID_CREW_MEMBERS);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    reportClient.createAndStoreProcessReport("otherProcess");

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .terms("The")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );
  }

  @Test
  public void searchForCandidateGroups_forCombinedReport() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport("otherProcess", CANDIDATE_GROUP_ID_CREW_MEMBERS);

    final String reportId1 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String reportId2 = reportClient.createAndStoreProcessReport("otherProcess");
    final String combinedReportId = reportClient.createNewCombinedReport(reportId1, reportId2);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(combinedReportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactly(
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS),
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );
  }

  @Test
  public void searchForCandidateGroups_forCombinedReport_duplicateReports() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport("otherProcess", CANDIDATE_GROUP_ID_CREW_MEMBERS);

    final String reportId1 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String reportId2 = reportClient.createAndStoreProcessReport("otherProcess");
    final String combinedReportId = reportClient.createNewCombinedReport(reportId1, reportId2);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(reportId1, combinedReportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactly(
        new GroupDto(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS),
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );
  }

  @Test
  public void searchForCandidateGroups_duplicateReportSources() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);

    final String reportId1 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String reportId2 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String combinedReportId = reportClient.createNewCombinedReport(reportId1, reportId2);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(reportId1, combinedReportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );
  }

  @Test
  public void searchForCandidateGroups_forReports_decisionReportsAreIgnored() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);

    final String processReportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String decisionReportId = reportClient.createAndStoreDecisionReport("decisionKey");

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(processReportId, decisionReportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new GroupDto(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS)
      );
  }

  @Test
  public void searchForCandidateGroups_fakeReportIdsIsIgnored() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
    reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList("doesntExist"))
        .build()
    );
    // then
    assertThat(searchResponse.getTotal()).isZero();
    assertThat(searchResponse.getResult()).isEmpty();
  }

  public ProcessInstanceEngineDto startSimpleUserTaskProcessWithCandidateGroup() {
    return engineIntegrationExtension.deployAndStartProcess(
      Bpmn.createExecutableProcess("aProcess")
        .startEvent()
        .userTask().camundaCandidateGroups("marketing")
        .userTask().camundaCandidateGroups("sales")
        .endEvent()
        .done()
    );
  }

  private ProcessInstanceEngineDto startSimpleUserTaskProcessWithCandidateGroupAndImport(final String candidateGroup) {
    return startSimpleUserTaskProcessWithCandidateGroupAndImport("key", candidateGroup);
  }

  private ProcessInstanceEngineDto startSimpleUserTaskProcessWithCandidateGroupAndImport(final String definitionKey,
                                                                                         final String candidateGroup) {
    return startSimpleUserTaskProcessWithCandidateGroupAndImport(definitionKey, candidateGroup, null);
  }

  private ProcessInstanceEngineDto startSimpleUserTaskProcessWithCandidateGroupAndImport(final String definitionKey,
                                                                                         final String candidateGroup,
                                                                                         final String tenantId) {
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getUserTaskDiagramWithCandidateGroup(definitionKey, candidateGroup), tenantId
    );
    importAllEngineEntitiesFromScratch();
    return processInstanceEngineDto;
  }
}
