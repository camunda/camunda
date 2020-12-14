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

  private void startSimpleUserTaskProcessWithCandidateGroupAndImport(final String candidateGroup) {
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithCandidateGroup(candidateGroup));
    importAllEngineEntitiesFromScratch();
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
}
