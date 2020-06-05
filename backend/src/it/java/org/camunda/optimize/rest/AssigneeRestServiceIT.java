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

public class AssigneeRestServiceIT extends AbstractIT {

  public static final String PROC_DEF_KEY = "aProcess";

  @Test
  public void getAssignees() {
    ProcessInstanceEngineDto processInstanceEngineDto = startSimpleUserTaskProcessWithAssignee();

    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      PROC_DEF_KEY,
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );
    List<String> assignees = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAssigneesRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    assertThat(assignees).hasSize(2);
    assertThat(assignees).containsOnly("demo", "john");
  }

  @Test
  public void getAssigneesForNonExistingProcDef() {
    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "lol",
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );

    List<String> response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAssigneesRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    assertThat(response.isEmpty()).isTrue();
  }

  public ProcessInstanceEngineDto startSimpleUserTaskProcessWithAssignee() {
    return engineIntegrationExtension.deployAndStartProcess(
      Bpmn.createExecutableProcess(PROC_DEF_KEY)
        .startEvent()
        .userTask().camundaAssignee("demo")
        .userTask().camundaAssignee("john")
        .endEvent()
        .done()
    );
  }
}
