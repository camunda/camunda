/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;

public class AssigneeRestServiceIT extends AbstractIT {

  public static final String ASSIGNEE_ID_JOHN = "john";
  public static final String JOHN_FIRST_NAME = "The";
  public static final String JOHN_LAST_NAME = "Imposter";

  public static final String ASSIGNEE_ID_JEAN = "jean";
  public static final String JEAN_FIRST_NAME = "True";
  public static final String JEAN_LAST_NAME = "CrewMember";

  @Test
  public void getAssignees() {
    // given
    ProcessInstanceEngineDto processInstanceEngineDto = startSimpleUserTaskProcessWithAssignee();

    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      BpmnModels.DEFAULT_PROCESS_ID,
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );
    List<String> assignees = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAssigneesRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(assignees).containsOnly("demo", "john");
  }

  @Test
  public void getAssigneesForNonExistingProcDef() {
    // when
    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "lol",
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );

    List<String> response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAssigneesRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(response.isEmpty()).isTrue();
  }

  @Test
  public void getAssigneeById() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    // when
    final List<UserDto> assignees = assigneesClient.getAssigneesByIdsWithoutAuthentication(ImmutableList.of(ASSIGNEE_ID_JOHN));

    // then
    assertThat(assignees)
      .extracting(
        UserDto::getFirstName, UserDto::getLastName, IdentityWithMetadataResponseDto::getName, UserDto::getEmail
      )
      .singleElement()
      .isEqualTo(Tuple.tuple(
        JOHN_FIRST_NAME, JOHN_LAST_NAME, JOHN_FIRST_NAME + " " + JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN
      ));
  }

  @Test
  public void getAssigneeByIdNotExistingReflectsIdAsNameBack() {
    // when
    final List<UserDto> assignees = assigneesClient.getAssigneesByIdsWithoutAuthentication(ImmutableList.of(ASSIGNEE_ID_JOHN));

    // then
    assertThat(assignees)
      .extracting(
        UserDto::getFirstName, UserDto::getLastName, IdentityWithMetadataResponseDto::getName, UserDto::getEmail
      )
      .singleElement()
      // id will be reflected as name when there is no data
      .isEqualTo(Tuple.tuple(null, null, ASSIGNEE_ID_JOHN, null));
  }

  @Test
  public void getAssigneesByIds() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    // when
    final List<UserDto> assignees =
      assigneesClient.getAssigneesByIdsWithoutAuthentication(ImmutableList.of(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN));

    // then
    assertThat(assignees)
      .hasSize(2)
      .extracting(
        UserDto::getFirstName, UserDto::getLastName, IdentityWithMetadataResponseDto::getName, UserDto::getEmail
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          JOHN_FIRST_NAME,
          JOHN_LAST_NAME,
          JOHN_FIRST_NAME + " " + JOHN_LAST_NAME,
          ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN
        ),
        Tuple.tuple(
          JEAN_FIRST_NAME,
          JEAN_LAST_NAME,
          JEAN_FIRST_NAME + " " + JEAN_LAST_NAME,
          ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN
        )
      );
  }

  @Test
  public void getAssigneesByIdsOneNotExisting() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    // when
    final List<UserDto> assignees =
      assigneesClient.getAssigneesByIdsWithoutAuthentication(ImmutableList.of(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN));

    // then
    assertThat(assignees)
      .hasSize(2)
      .extracting(
        UserDto::getFirstName, UserDto::getLastName, IdentityWithMetadataResponseDto::getName, UserDto::getEmail
      )
      .containsExactly(
        Tuple.tuple(
          JOHN_FIRST_NAME,
          JOHN_LAST_NAME,
          JOHN_FIRST_NAME + " " + JOHN_LAST_NAME,
          ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN
        ),
        Tuple.tuple(
          null,
          null,
          // id will be reflected as name when there is no data
          ASSIGNEE_ID_JEAN,
          null
        )
      );
  }

  private ProcessInstanceEngineDto startSimpleUserTaskProcessWithAssignee() {
    return engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getDoubleUserTaskDiagramWithAssignees("demo", "john")
    );
  }

  private void startSimpleUserTaskProcessWithAssigneeAndImport(final String assignee) {
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithAssignee(assignee));
    importAllEngineEntitiesFromScratch();
  }
}
