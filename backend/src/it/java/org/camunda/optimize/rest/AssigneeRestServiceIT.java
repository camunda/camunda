/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
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
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;

public class AssigneeRestServiceIT extends AbstractIT {

  private static final String ASSIGNEE_ID_JOHN = "john";
  private static final String JOHN_FIRST_NAME = "The";
  private static final String JOHN_LAST_NAME = "Imposter";

  private static final String ASSIGNEE_ID_JEAN = "jean";
  private static final String JEAN_FIRST_NAME = "The";
  private static final String JEAN_LAST_NAME = "CrewMember";

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
  public void searchForAssignees_forDefinition_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForAssigneesRequest(AssigneeCandidateGroupDefinitionSearchRequestDto.builder().build())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void searchForAssignees_forReports_unauthorized() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildSearchForAssigneesRequest(AssigneeCandidateGroupReportSearchRequestDto.builder().build())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void searchForAssignees_forDefinition_missingKeyIsRejected() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForAssigneesRequest(AssigneeCandidateGroupDefinitionSearchRequestDto.builder().build())
      .execute();

    // then the status code is bad request
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void searchForAssignees_forReports_missingIdsIsRejected() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSearchForAssigneesRequest(AssigneeCandidateGroupReportSearchRequestDto.builder().build())
      .execute();

    // then the status code is bad request
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void searchForAssignees_forDefinition_noSearchTerm() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
    final String userIdNotInEngine = "anotherUserID";
    startSimpleUserTaskProcessWithAssigneeAndImport(userIdNotInEngine);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(3);
    assertThat(searchResponse.getResult())
      .hasSize(3)
      .containsExactlyInAnyOrder(
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(userIdNotInEngine)
      );
  }

  @Test
  public void searchForAssignees_forReports_noSearchTerm() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
    final String userIdNotInEngine = "anotherUserID";
    startSimpleUserTaskProcessWithAssigneeAndImport(userIdNotInEngine);
    final String reportId =
      reportClient.createAndStoreProcessReport(processInstanceEngineDto.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(3);
    assertThat(searchResponse.getResult())
      .hasSize(3)
      .containsExactlyInAnyOrder(
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(userIdNotInEngine)
      );
  }

  @Test
  public void searchForAssignees_forReportsWithMultipleDefinitions_noSearchTerm() {
    // given
    final String key1 = "key1";
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(key1, ASSIGNEE_ID_JOHN);
    final String key2 = "key2";
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(key2, ASSIGNEE_ID_JEAN);

    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .definitions(List.of(new ReportDataDefinitionDto(key1), new ReportDataDefinitionDto(key2)))
      .build();
    final String reportId = reportClient.createSingleProcessReport(reportDataDto);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
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
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(definitionKey1)
        .build()
    );
    final IdentitySearchResultResponseDto otherDefinitionSearchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
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
  public void searchForAssigneesNoSearchTerm_otherReportAssigneeIsFiltered() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final String definitionKey1 = "process";
    startSimpleUserTaskProcessWithAssigneeAndImport(definitionKey1, ASSIGNEE_ID_JOHN);
    final String definitionKey2 = "otherProcess";
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(definitionKey2, ASSIGNEE_ID_JEAN);

    final String reportId1 = reportClient.createAndStoreProcessReport(definitionKey1);
    final String reportId2 = reportClient.createAndStoreProcessReport(definitionKey2);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId1))
        .build()
    );
    final IdentitySearchResultResponseDto otherReportSearchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId2))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );

    assertThat(otherReportSearchResponse.getTotal()).isEqualTo(1);
    assertThat(otherReportSearchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesNoSearchTerm_forDefinition_tenantAssigneeIsFiltered() {
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
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
        .build()
    );
    final IdentitySearchResultResponseDto tenant1SearchResponse = assigneesClient.searchForAssignees(
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
  public void searchForAssigneesNoSearchTerm_forReports_tenantAssigneeIsFiltered() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(
      instance.getProcessDefinitionKey(), ASSIGNEE_ID_JEAN, tenant1
    );

    final String reportId1 = reportClient.createAndStoreProcessReport(
      instance.getProcessDefinitionKey()
    );
    final String reportId2 = reportClient.createAndStoreProcessReport(
      instance.getProcessDefinitionKey(),
      Collections.singletonList(tenant1)
    );

    // when
    final IdentitySearchResultResponseDto noTenantSearchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId1))
        .build()
    );
    final IdentitySearchResultResponseDto tenant1SearchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId2))
        .build()
    );

    // then
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
  public void searchForAssigneesNoSearchTerm_forReports_sameDefinitionDifferentTenants() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance1 =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    final ProcessInstanceEngineDto instance2 = startSimpleUserTaskProcessWithAssigneeAndImport(
      instance1.getProcessDefinitionKey(), ASSIGNEE_ID_JEAN, tenant1
    );

    final String reportId1 = reportClient.createAndStoreProcessReport(
      instance1.getProcessDefinitionKey(),
      Collections.singletonList(instance1.getTenantId())
    );
    final String reportId2 = reportClient.createAndStoreProcessReport(
      instance2.getProcessDefinitionKey(),
      Collections.singletonList(instance2.getTenantId())
    );

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(reportId1, reportId2))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactly(
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesNoSearchTerm_forReports_multipleDefinitions() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final String definitionKey1 = "process";
    startSimpleUserTaskProcessWithAssigneeAndImport(definitionKey1, ASSIGNEE_ID_JOHN);
    final String definitionKey2 = "otherProcess";
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(definitionKey2, ASSIGNEE_ID_JEAN);

    final String reportId1 = reportClient.createAndStoreProcessReport(definitionKey1);
    final String reportId2 = reportClient.createAndStoreProcessReport(definitionKey2);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(reportId1, reportId2))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactly(
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesWithSearchTerm_forDefinition() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);


    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
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
  public void searchForAssigneesWithSearchTerm_forDefinition_userNotInEngine() {
    // given
    final String userIdNotInEngine = "anotherUserID";
    final ProcessInstanceEngineDto instance = startSimpleUserTaskProcessWithAssigneeAndImport(userIdNotInEngine);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
        .processDefinitionKey(instance.getProcessDefinitionKey())
        .terms("userID")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .hasSize(1)
      .containsExactlyInAnyOrder(
        new UserDto(userIdNotInEngine)
      );
  }

  @Test
  public void searchForAssigneesWithSearchTerm_forReports() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance = startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .terms("John")
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

  @Test
  public void searchForAssigneesWithSearchTerm_forReports_userNotInEngine() {
    // given
    final String userIdNotInEngine = "anotherUserID";
    final ProcessInstanceEngineDto instance = startSimpleUserTaskProcessWithAssigneeAndImport(userIdNotInEngine);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
        .terms("userID")
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(1);
    assertThat(searchResponse.getResult())
      .singleElement()
      .isEqualTo(
        new UserDto(userIdNotInEngine)
      );
  }

  @Test
  public void searchForAssigneesWithSearchTerm_forDefinition_multipleHits() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
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
  public void searchForAssigneesWithSearchTerm_forReports_multipleHits() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance = startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
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
  public void searchForAssigneesWithSearchTerm_forDefinition_multipleHitsLimited() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupDefinitionSearchRequestDto.builder()
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
  public void searchForAssigneesWithSearchTerm_forReports_multipleHitsLimited() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance = startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
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
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssigneesWithSearchTerm_otherReportAssigneeIsFiltered() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance = startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport("otherProcess", ASSIGNEE_ID_JEAN);

    final String reportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    reportClient.createAndStoreProcessReport("otherProcess");

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(reportId))
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

  @Test
  public void searchForAssignees_forCombinedReport() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance = startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(
      instance.getProcessDefinitionKey(), ASSIGNEE_ID_JEAN, tenant1
    );

    final String reportId1 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String reportId2 = reportClient.createAndStoreProcessReport(
      instance.getProcessDefinitionKey(),
      Collections.singletonList(tenant1)
    );
    final String combinedReportId = reportClient.createNewCombinedReport(reportId1, reportId2);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList(combinedReportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactly(
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssignees_forCombinedReport_duplicateReports() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(
      instance.getProcessDefinitionKey(), ASSIGNEE_ID_JEAN, tenant1
    );

    final String singleReport1 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String singleReport2 = reportClient.createAndStoreProcessReport(
      instance.getProcessDefinitionKey(),
      Collections.singletonList(tenant1)
    );
    final String combinedReportId = reportClient.createNewCombinedReport(singleReport1, singleReport2);

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(singleReport1, combinedReportId))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isEqualTo(2);
    assertThat(searchResponse.getResult())
      .hasSize(2)
      .containsExactly(
        new UserDto(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME, ASSIGNEE_ID_JEAN + DEFAULT_EMAIL_DOMAIN),
        new UserDto(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN)
      );
  }

  @Test
  public void searchForAssignees_duplicateReportSources() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance = startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    final String reportId1 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String reportId2 = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(reportId1, reportId2))
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

  @Test
  public void searchForAssignees_forReports_decisionReportsAreIgnored() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto instance =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.deployAndStartDecisionDefinition(createSimpleDmnModel("decisionKey"));

    final String processReportId = reportClient.createAndStoreProcessReport(instance.getProcessDefinitionKey());
    final String decisionReportId = reportClient.createAndStoreDecisionReport("decisionKey");

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Arrays.asList(processReportId, decisionReportId))
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

  @Test
  public void searchForAssignees_forReports_fakeReportIdIsIgnored() {
    // given
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
    reportClient.createAndStoreProcessReport(processInstanceEngineDto.getProcessDefinitionKey());

    // when
    final IdentitySearchResultResponseDto searchResponse = assigneesClient.searchForAssignees(
      AssigneeCandidateGroupReportSearchRequestDto.builder()
        .reportIds(Collections.singletonList("doesntExist"))
        .build()
    );

    // then
    assertThat(searchResponse.getTotal()).isZero();
    assertThat(searchResponse.getResult()).isEmpty();
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
