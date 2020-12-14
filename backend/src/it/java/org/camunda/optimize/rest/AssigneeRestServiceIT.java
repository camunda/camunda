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
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;

public class AssigneeRestServiceIT extends AbstractIT {

  public static final String ASSIGNEE_ID_JOHN = "john";
  public static final String JOHN_FIRST_NAME = "The";
  public static final String JOHN_LAST_NAME = "Imposter";

  public static final String ASSIGNEE_ID_JEAN = "jean";
  public static final String JEAN_FIRST_NAME = "The";
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
    final List<UserDto> assignees = assigneesClient.getAssigneesByIdsWithoutAuthentication(
      ImmutableList.of(ASSIGNEE_ID_JOHN)
    );

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
    final List<UserDto> assignees = assigneesClient.getAssigneesByIdsWithoutAuthentication(
      ImmutableList.of(ASSIGNEE_ID_JOHN)
    );

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

  @Test
  public void searchForAssignees_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForAssigneesRequest(AssigneeCandidateGroupSearchRequestDto.builder().build())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void searchForAssignees_missingKeyIsRejected() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForAssigneesRequest(AssigneeCandidateGroupSearchRequestDto.builder().build())
      .execute();

    // then the status code is bad request
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void searchForAssigneesNoSearchTerm() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesNoSearchTerm_otherProcessAssigneeIsFiltered() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final String definitionKey1 = "process";
    startSimpleUserTaskProcessWithAssigneeAndImport(definitionKey1, ASSIGNEE_ID_JOHN);
    final String definitionKey2 = "otherProcess";
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(definitionKey2, ASSIGNEE_ID_JEAN);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(definitionKey1)
        .build()
    );
    final IdentitySearchResultResponseDto otherDefinitionSearchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(definitionKey2)
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );

    assertThat(otherDefinitionSearchResponse.getTotal()).isEqualTo(1);
    assertThat(otherDefinitionSearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesNoSearchTerm_tenantAssigneeIsFiltered() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(
      processInstanceEngineDto.getProcessDefinitionKey(), ASSIGNEE_ID_JEAN, tenant1
    );

    // when
    final IdentitySearchResultResponseDto noTenantSearchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .build()
    );
    final IdentitySearchResultResponseDto tenant1SearchResponse = assigneesClient.searchForAssignees(
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
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );

    assertThat(tenant1SearchResponse.getTotal()).isEqualTo(1);
    assertThat(tenant1SearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesWithSearchTerm() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);


    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .terms("John")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .hasSize(1)
      .containsExactlyInAnyOrder(
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesWithSearchTermMultipleHits() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .terms("J")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesWithSearchTermMultipleHitsLimited() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .terms("J")
        .limit(1)
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult()).hasSize(1);
  }

  @Test
  public void searchForAssigneesWithSearchTerm_otherProcessAssigneeIsFiltered() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport("otherProcess", ASSIGNEE_ID_JEAN);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
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
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  private ProcessInstanceEngineDto startSimpleUserTaskProcessWithAssignee() {
    return engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getDoubleUserTaskDiagramWithAssignees("demo", "john")
    );
  }

  private ProcessInstanceEngineDto startSimpleUserTaskProcessWithAssigneeAndImport(final String assignee) {
    return startSimpleUserTaskProcessWithAssigneeAndImport("key", assignee);
  }

  private ProcessInstanceEngineDto startSimpleUserTaskProcessWithAssigneeAndImport(final String definitionKey,
                                                                                   final String assignee) {
    return startSimpleUserTaskProcessWithAssigneeAndImport(definitionKey, assignee, null);
  }

  private ProcessInstanceEngineDto startSimpleUserTaskProcessWithAssigneeAndImport(final String definitionKey,
                                                                                   final String assignee,
                                                                                   final String tenantId) {
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getUserTaskDiagramWithAssignee(definitionKey, assignee), tenantId
    );
    importAllEngineEntitiesFromScratch();
    return processInstanceEngineDto;
  }
}
