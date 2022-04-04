/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsOwnerDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class ProcessGoalsOwnerIT extends AbstractProcessGoalsIT {

  @Test
  public void setProcessOwner_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(DEF_KEY, new ProcessGoalsOwnerDto(DEFAULT_USERNAME))
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void setProcessOwner_noDefinitionExistsForKey() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(DEF_KEY, new ProcessGoalsOwnerDto(DEFAULT_USERNAME))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private static Stream<ProcessGoalsOwnerDto> validOwners() {
    return Stream.of(new ProcessGoalsOwnerDto(null), new ProcessGoalsOwnerDto(DEFAULT_USERNAME));
  }

  @ParameterizedTest
  @MethodSource("validOwners")
  public void setProcessOwner_validOwnerSetting(final ProcessGoalsOwnerDto ownerDto) {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(DEF_KEY, ownerDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertExpectedProcessOwner(DEF_KEY, ownerDto.getId());
  }

  @Test
  public void setProcessOwner_replaceOwnerAlreadyExists() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();
    setOwnerForProcess(DEF_KEY, KERMIT_USER);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(DEF_KEY, new ProcessGoalsOwnerDto(DEFAULT_USERNAME))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertExpectedProcessOwner(DEF_KEY, DEFAULT_USERNAME);
  }

  @Test
  public void setProcessOwner_removeExistingOwner() {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();
    setOwnerForProcess(DEF_KEY, DEFAULT_USERNAME);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(DEF_KEY, new ProcessGoalsOwnerDto(null))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertExpectedProcessOwner(DEF_KEY, null);
  }

  @Test
  public void setProcessOwner_invalidOwner() {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();
    setOwnerForProcess(DEF_KEY, DEFAULT_USERNAME);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(DEF_KEY, null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void setProcessOwner_eventBasedProcess() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      DEF_KEY, new IdentityDto(DEFAULT_USERNAME, IdentityType.USER));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(DEF_KEY, new ProcessGoalsOwnerDto(DEFAULT_USERNAME))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertExpectedProcessOwner(DEF_KEY, DEFAULT_USERNAME);
  }

  @Test
  public void setProcessOwner_notAuthorizedToProcess() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    final String defKey = "notAuthorized";
    deploySimpleProcessDefinition(defKey);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(defKey, new ProcessGoalsOwnerDto(DEFAULT_USERNAME))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void setProcessOwner_notAuthorizedToProcessOwner() {
    // given
    final String defKey = "notAuthorized";
    deploySimpleProcessDefinition(defKey);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(DEFAULT_USERNAME, RESOURCE_TYPE_USER);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildSetProcessOwnerRequest(defKey, new ProcessGoalsOwnerDto(DEFAULT_USERNAME))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

}
