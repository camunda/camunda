/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateGroupsRestServiceIT extends AbstractIT {

  public static final String CANDIDATE_GROUP_ID_IMPOSTERS = "imposters";
  public static final String CANDIDATE_GROUP_NAME_IMPOSTERS = "The Evil Imposters";

  public static final String CANDIDATE_GROUP_ID_CREW_MEMBERS = "crewMembers";
  public static final String CANDIDATE_GROUP_NAME_CREW_MEMBERS = "The Crew Members";

  @Test
  public void getCandidateGroups() {
    // given
    ProcessInstanceEngineDto processInstanceEngineDto = startSimpleUserTaskProcessWithCandidateGroup();

    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "aProcess",
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );
    List<String> assignees = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCandidateGroupsRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(assignees).containsOnly("sales", "marketing");
  }

  @Test
  public void getCandidateGroupsForNonExistingProcDef() {
    // when
    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "lol",
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );

    List<String> response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCandidateGroupsRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(response.isEmpty()).isTrue();
  }

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
  public void searchForCandidateGroups_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForCandidateGroupsRequest(AssigneeCandidateGroupSearchRequestDto.builder().build())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void searchForCandidateGroups_missingKeyIsRejected() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForCandidateGroupsRequest(AssigneeCandidateGroupSearchRequestDto.builder().build())
      .execute();

    // then the status code is bad request
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void searchForCandidateGroupsNoSearchTerm() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
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
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(definitionKey1)
        .build()
    );
    final IdentitySearchResultResponseDto otherDefinitionSearchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupSearchRequestDto.builder()
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
  public void searchForCandidateGroupsNoSearchTerm_tenantAssigneeIsFiltered() {
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
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .build()
    );
    final IdentitySearchResultResponseDto tenant1SearchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .tenantIds(ImmutableList.of(tenant1))
        .build()
    );

    //then
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
  public void searchForCandidateGroupsWithSearchTerm() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);


    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupSearchRequestDto.builder()
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
  public void searchForCandidateGroupsWithSearchTermMultipleHits() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupSearchRequestDto.builder()
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
  public void searchForCandidateGroupsWithSearchTermMultipleHitsLimited() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupSearchRequestDto.builder()
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
  public void searchForCandidateGroupsWithSearchTerm_otherProcessCandidateGroupIsFiltered() {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport("otherProcess", CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final IdentitySearchResultResponseDto searchResponse = candidateGroupClient.searchForCandidateGroups(
      AssigneeCandidateGroupSearchRequestDto.builder()
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
