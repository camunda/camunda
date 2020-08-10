/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateGroupsRestServiceIT extends AbstractIT {

  @Test
  public void getCandidateGroups() {
    ProcessInstanceEngineDto processInstanceEngineDto = startSimpleUserTaskProcessWithCandidateGroup();

    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "aProcess",
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );
    List<String> assignees = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCandidateGroupsRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    assertThat(assignees).hasSize(2);
    assertThat(assignees).containsOnly("sales", "marketing");
  }

  @Test
  public void getCandidateGroupsForNonExistingProcDef() {
    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "lol",
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );

    List<String> response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCandidateGroupsRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    assertThat(response.isEmpty()).isTrue();
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
