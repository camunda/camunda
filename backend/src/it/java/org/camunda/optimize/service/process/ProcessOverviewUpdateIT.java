/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOwnerResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessOverviewUpdateIT extends AbstractIT {

  private static final String DEF_KEY = "def_key";

  @Test
  public void updateProcess_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(DEF_KEY, new ProcessUpdateDto(DEFAULT_USERNAME, new ProcessDigestRequestDto()))
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void updateProcess_noDefinitionExistsForKey() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(DEF_KEY, new ProcessUpdateDto(DEFAULT_USERNAME, new ProcessDigestRequestDto()))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }


  private static Stream<String> validOwnerIds() {
    return Stream.of(null, DEFAULT_USERNAME, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource("validOwnerIds")
  public void updateProcess_setOwner(final String ownerId) {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(DEF_KEY, new ProcessUpdateDto(ownerId, new ProcessDigestRequestDto()))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertExpectedProcessOwner(DEF_KEY, ownerId);
  }

  @ParameterizedTest
  @MethodSource("validOwnerIds")
  public void updateProcess_replaceExistingOwner(final String ownerId) {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    importAllEngineEntitiesFromScratch();
    setProcessConfiguration(DEF_KEY, new ProcessUpdateDto(KERMIT_USER, new ProcessDigestRequestDto()));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(DEF_KEY, new ProcessUpdateDto(ownerId, new ProcessDigestRequestDto()))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertExpectedProcessOwner(DEF_KEY, ownerId);
  }

  @Test
  public void updateProcess_setEventBasedProcessOwnerNotPossible() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      DEF_KEY, new IdentityDto(DEFAULT_USERNAME, IdentityType.USER));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(DEF_KEY, new ProcessUpdateDto(DEFAULT_USERNAME, new ProcessDigestRequestDto()))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateProcess_notAuthorizedToProcess() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    final String defKey = "notAuthorized";
    deploySimpleProcessDefinition(defKey);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(defKey, new ProcessUpdateDto(DEFAULT_USERNAME, new ProcessDigestRequestDto()))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateProcess_notAuthorizedToProcessOwner() {
    // given
    final String defKey = "notAuthorized";
    deploySimpleProcessDefinition(defKey);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(DEFAULT_USERNAME, RESOURCE_TYPE_USER);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(defKey, new ProcessUpdateDto(DEFAULT_USERNAME, new ProcessDigestRequestDto()))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateProcess_nullDigest() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(DEF_KEY, new ProcessUpdateDto(DEFAULT_USERNAME, null))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateProcess_setDigestAndOwner() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    final ProcessDigestRequestDto digestConfig = new ProcessDigestRequestDto(true);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(DEF_KEY, new ProcessUpdateDto(DEFAULT_USERNAME, digestConfig))
      .execute();

    // then
    assertThat(processOverviewClient.getProcessOverviews()).singleElement()
      .extracting(
        ProcessOverviewResponseDto::getProcessDefinitionKey,
        ProcessOverviewResponseDto::getOwner,
        ProcessOverviewResponseDto::getDigest
      )
      .containsExactly(
        DEF_KEY,
        convertToOwnerResponse(DEFAULT_USERNAME, DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME),
        convertToDigestResponse(digestConfig)
      );
  }

  @Test
  public void updateProcess_updateDigestAndOwner() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    importAllEngineEntitiesFromScratch();
    final ProcessDigestRequestDto initialDigestConfig = new ProcessDigestRequestDto(true);
    processOverviewClient.updateProcess(DEF_KEY, DEFAULT_USERNAME, initialDigestConfig);

    // then
    assertThat(processOverviewClient.getProcessOverviews()).singleElement()
      .extracting(
        ProcessOverviewResponseDto::getProcessDefinitionKey,
        ProcessOverviewResponseDto::getOwner,
        ProcessOverviewResponseDto::getDigest
      )
      .containsExactly(
        DEF_KEY,
        convertToOwnerResponse(DEFAULT_USERNAME, DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME),
        convertToDigestResponse(initialDigestConfig)
      );

    // when
    final ProcessDigestRequestDto updatedDigestConfig = new ProcessDigestRequestDto(true);
    processOverviewClient.updateProcess(DEF_KEY, KERMIT_USER, updatedDigestConfig);

    // then
    assertThat(processOverviewClient.getProcessOverviews()).singleElement()
      .extracting(
        ProcessOverviewResponseDto::getProcessDefinitionKey,
        ProcessOverviewResponseDto::getOwner,
        ProcessOverviewResponseDto::getDigest
      )
      .containsExactly(
        DEF_KEY,
        convertToOwnerResponse(KERMIT_USER, DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME),
        convertToDigestResponse(updatedDigestConfig)
      );
  }

  @Test
  public void updateProcess_digestConfigWithNoOwner() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(
        DEF_KEY, new ProcessUpdateDto(null, new ProcessDigestRequestDto(true)))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateProcess_digestConfigIsPersistedEvenIfDisabled() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    final ProcessDigestRequestDto digestConfig = new ProcessDigestRequestDto(false);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(
        DEF_KEY, new ProcessUpdateDto(DEFAULT_USERNAME, digestConfig))
      .execute();

    // then
    assertThat(processOverviewClient.getProcessOverviews()).singleElement()
      .extracting(
        ProcessOverviewResponseDto::getProcessDefinitionKey,
        ProcessOverviewResponseDto::getOwner,
        ProcessOverviewResponseDto::getDigest
      )
      .containsExactly(
        DEF_KEY,
        convertToOwnerResponse(DEFAULT_USERNAME, DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME),
        convertToDigestResponse(digestConfig)
      );
  }

  private ProcessDigestResponseDto convertToDigestResponse(final ProcessDigestRequestDto digest) {
    final ProcessDigestResponseDto digestResponse = new ProcessDigestResponseDto();
    digestResponse.setEnabled(digest.isEnabled());
    return digestResponse;
  }

  private ProcessOwnerResponseDto convertToOwnerResponse(final String ownerId, final String ownerName) {
    final ProcessOwnerResponseDto ownerResponseDto = new ProcessOwnerResponseDto();
    ownerResponseDto.setId(ownerId);
    ownerResponseDto.setName(ownerName);
    return ownerResponseDto;
  }

  private void deploySimpleProcessDefinition(String processDefinitionKey) {
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(processDefinitionKey));
  }

  private void assertExpectedProcessOwner(final String defKey, final String expectedOwnerId) {
    assertThat(getProcessOverView())
      .filteredOn(def -> def.getProcessDefinitionKey().equals(defKey))
      .extracting(ProcessOverviewResponseDto::getOwner)
      .singleElement()
      .satisfies(processOwner -> assertThat(processOwner)
        .isEqualTo(expectedOwnerId == null ? new ProcessOwnerResponseDto()
                     : new ProcessOwnerResponseDto(expectedOwnerId, embeddedOptimizeExtension.getIdentityService()
          .getIdentityNameById(expectedOwnerId)
          .orElseThrow(() -> new OptimizeIntegrationTestException("Could not find default user in cache")))));
  }

  private List<ProcessOverviewResponseDto> getProcessOverView() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessOverviewRequest(null)
      .executeAndReturnList(ProcessOverviewResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private void setProcessConfiguration(final String processDefinitionKey, final ProcessUpdateDto processUpdateDto) {
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessRequest(processDefinitionKey, processUpdateDto)
      .execute();
  }

}