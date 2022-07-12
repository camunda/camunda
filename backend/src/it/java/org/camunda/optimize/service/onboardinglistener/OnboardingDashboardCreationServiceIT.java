/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboardinglistener;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.engine.AuthorizationClient.SPIDERMAN_FULLNAME;
import static org.camunda.optimize.test.engine.AuthorizationClient.SPIDERMAN_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;

public class OnboardingDashboardCreationServiceIT extends AbstractIT {

  @Test
  public void createDashboardAndCollectionAutomatically() {
    // given
    final String processKey = "some_tomatoes_are_green";
    createDashboardAndCollectionAsDemoUser(processKey);

    // when
    final DashboardDefinitionRestDto createdDashboard = dashboardClient.getDashboard(processKey);
    final CollectionDefinitionRestDto createdCollection = collectionClient.getCollectionById(processKey);

    // then
    assertThat(createdDashboard).isNotNull();
    assertThat(createdDashboard.getReportIds().size()).isEqualTo(12);
    assertThat(createdDashboard.getCollectionId()).isEqualTo(processKey);
    assertThat(createdDashboard.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(createdDashboard.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(createdDashboard.isManagementDashboard()).isFalse();
    assertThat(createdDashboard.getId()).isEqualTo(processKey);
    assertThat(createdCollection).isNotNull();
    assertThat(createdCollection.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(createdCollection.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(createdCollection.getId()).isEqualTo(processKey);
    assertThat(createdCollection.isAutomaticallyCreated()).isTrue();
    CollectionDataDto data = createdCollection.getData();
    CollectionScopeEntryDto expectedScope = new CollectionScopeEntryDto(DefinitionType.PROCESS, processKey);
    assertThat(data.getScope().size()).isEqualTo(1);
    assertThat(data.getScope().get(0)).isEqualTo(expectedScope);
    CollectionRoleRequestDto expectedRole = new CollectionRoleRequestDto(
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER),
      RoleType.MANAGER);
    assertThat(data.getRoles().size()).isEqualTo(1);
    assertThat(data.getRoles().get(0)).isEqualTo(expectedRole);
  }

  @Test
  public void whenADashboardAndCollectionAlreadyExistthenAddUserAsEditor() {
    // given
    final String processKey = "most_potatoes_are_yellow";
    createDashboardAndCollectionAsDemoUser(processKey);
    authorizationClient.addSpidermanUserAndGrantAccessToOptimize();

    // when
    // Make sure that the owner is the demo user
    final DashboardDefinitionRestDto dashboardForProcessDefinition = dashboardClient.getDashboard(processKey);
    assertThat(dashboardForProcessDefinition.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    // Simulate a call from Spiderman now
    entitiesClient.getEntityNamesAsUser(processKey, processKey, null, null, SPIDERMAN_USER, SPIDERMAN_USER);
    final CollectionDefinitionRestDto collectionForProcessDefinition = collectionClient.getCollectionById(processKey);
    
    // then
    assertThat(collectionForProcessDefinition).isNotNull();
    assertThat(collectionForProcessDefinition.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(collectionForProcessDefinition.getLastModifier()).isEqualTo(SPIDERMAN_FULLNAME);
    assertThat(collectionForProcessDefinition.getId()).isEqualTo(processKey);
    assertThat(collectionForProcessDefinition.isAutomaticallyCreated()).isTrue();
    CollectionDataDto data = collectionForProcessDefinition.getData();
    CollectionScopeEntryDto expectedScope = new CollectionScopeEntryDto(DefinitionType.PROCESS, processKey);
    assertThat(data.getScope().size()).isEqualTo(1);
    assertThat(data.getScope().get(0)).isEqualTo(expectedScope);
    CollectionRoleRequestDto expectedRoleDemo = new CollectionRoleRequestDto(
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER), RoleType.MANAGER);
    CollectionRoleRequestDto expectedRoleSpiderman = new CollectionRoleRequestDto(
      new IdentityDto(SPIDERMAN_USER, IdentityType.USER), RoleType.EDITOR);
    assertThat(data.getRoles().size()).isEqualTo(2);
    assertThat(data.getRoles()).containsExactlyInAnyOrder(expectedRoleDemo, expectedRoleSpiderman);
  }

  @Test
  public void doNotAddUserAsEditorIfNotAutomaticallyCreated() {
    // given
    final String collectionId = collectionClient.createNewCollection(DEFAULT_USERNAME, DEFAULT_USERNAME);
    final DashboardService dashboardService =
      embeddedOptimizeExtension.getApplicationContext().getBean(DashboardService.class);
    dashboardService.createNewDashboardWithPresetId(DEFAULT_USERNAME, new DashboardDefinitionRestDto(), collectionId);
    authorizationClient.addSpidermanUserAndGrantAccessToOptimize();

    // when
    // Make sure that the owner is the demo user
    final DashboardDefinitionRestDto dashboardForProcessDefinition = dashboardClient.getDashboard(collectionId);
    assertThat(dashboardForProcessDefinition.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    final CollectionDefinitionRestDto collectionForProcessDefinition = collectionClient.getCollectionById(collectionId);
    // Make sure the collection was not automatically created
    assertThat(collectionForProcessDefinition.isAutomaticallyCreated()).isFalse();
    // Make sure that dashboard and collection have the same ID to simulate a magic link situation
    assertThat(collectionForProcessDefinition.getId()).isEqualTo(dashboardForProcessDefinition.getId());
    // Simulate a call from Spiderman user now
    final EntityNameResponseDto response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(SPIDERMAN_USER, SPIDERMAN_USER)
      .buildGetEntityNamesRequest(new EntityNameRequestDto(collectionId, collectionId, null, null))
      .execute(EntityNameResponseDto.class, Response.Status.UNAUTHORIZED.getStatusCode());

    // then
    assertThat(collectionForProcessDefinition).isNotNull();
    assertThat(collectionForProcessDefinition.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(collectionForProcessDefinition.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(collectionForProcessDefinition.getId()).isEqualTo(collectionId);
    assertThat(collectionForProcessDefinition.isAutomaticallyCreated()).isFalse();
    CollectionDataDto data = collectionForProcessDefinition.getData();
    assertThat(data.getScope()).isEmpty();
    // Spiderman is not an editor
    CollectionRoleRequestDto expectedRoleDemo = new CollectionRoleRequestDto(
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER), RoleType.MANAGER);
    assertThat(data.getRoles().size()).isEqualTo(1);
    assertThat(data.getRoles()).containsExactly(expectedRoleDemo);
  }

  @Test
  public void doNotAddUserAsEditorIfDashboardAndCollectionNamesAreDifferent() {
    // given
    final String processKey = "all_grapefruits_are_sour";
    createDashboardAndCollectionAsDemoUser(processKey);
    authorizationClient.addSpidermanUserAndGrantAccessToOptimize();
    String emptyDashboardId = dashboardClient.createEmptyDashboard();

    // when
    // Simulate a call from Spiderman now
    entitiesClient.getEntityNamesAsUser(processKey, emptyDashboardId, null, null, SPIDERMAN_USER, SPIDERMAN_USER);
    final CollectionDefinitionRestDto collectionForProcessDefinition = collectionClient.getCollectionById(processKey);

    // then
    assertThat(collectionForProcessDefinition).isNotNull();
    assertThat(collectionForProcessDefinition.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(collectionForProcessDefinition.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(collectionForProcessDefinition.getId()).isEqualTo(processKey);
    assertThat(collectionForProcessDefinition.isAutomaticallyCreated()).isTrue();
    CollectionDataDto data = collectionForProcessDefinition.getData();
    // Spiderman is not an editor
    CollectionRoleRequestDto expectedRoleDemo = new CollectionRoleRequestDto(
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER), RoleType.MANAGER);
    assertThat(data.getRoles().size()).isEqualTo(1);
    assertThat(data.getRoles()).containsExactly(expectedRoleDemo);
  }

  @Test
  public void nothingHappensWithPermissionIfUserCallingAlreadyHasPermission() {
    // given
    final String processKey = "all_durians_smell_bad";
    createDashboardAndCollectionAsDemoUser(processKey);

    // when
    final DashboardDefinitionRestDto createdDashboard = dashboardClient.getDashboard(processKey);
    final CollectionDefinitionRestDto createdCollection = collectionClient.getCollectionById(processKey);
    // Simulate a call from Demo user now
    entitiesClient.getEntityNames(processKey, processKey, null, null);

    // then
    assertThat(createdDashboard).isNotNull();
    assertThat(createdDashboard.getReportIds().size()).isEqualTo(12);
    assertThat(createdDashboard.getCollectionId()).isEqualTo(processKey);
    assertThat(createdDashboard.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(createdDashboard.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(createdDashboard.isManagementDashboard()).isFalse();
    assertThat(createdDashboard.getId()).isEqualTo(processKey);

    assertThat(createdCollection).isNotNull();
    assertThat(createdCollection.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(createdCollection.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(createdCollection.getId()).isEqualTo(processKey);
    assertThat(createdCollection.isAutomaticallyCreated()).isTrue();
    CollectionDataDto data = createdCollection.getData();
    CollectionScopeEntryDto expectedScope = new CollectionScopeEntryDto(DefinitionType.PROCESS, processKey);
    assertThat(data.getScope().size()).isEqualTo(1);
    assertThat(data.getScope().get(0)).isEqualTo(expectedScope);
    CollectionRoleRequestDto expectedRole = new CollectionRoleRequestDto(
      new IdentityDto(DEFAULT_USERNAME, IdentityType.USER),
      RoleType.MANAGER);
    assertThat(data.getRoles().size()).isEqualTo(1);
    assertThat(data.getRoles().get(0)).isEqualTo(expectedRole);
  }

  @Test
  public void nothingHappensIfProcessDefinitionDoesntExist() {
    // given
    final String processKey = "Im_neither_a_fruit_nor_a_vegetable";
    // when
    embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(processKey, processKey, null, null))
      .execute(EntityNameResponseDto.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    // Check that Dashboard was not created
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDashboardRequest(processKey)
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response).containsSequence("Dashboard does not exist!");
    // Check that Collection was not created
    response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(processKey)
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response).containsSequence("Collection does not exist!");
  }

  private void createDashboardAndCollectionAsDemoUser(final String processKey) {
    deployAndStartSimpleServiceTaskProcess(processKey);
    importAllEngineEntitiesFromScratch();
    // Make sure Dashboard doesn't exist yet
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDashboardRequest(processKey)
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response).containsSequence("Dashboard does not exist!");
    // Make sure Collection doesn't exist yet
    response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(processKey)
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response).containsSequence("Collection does not exist!");
    final OnboardingDashboardCreationService onboardingDashboardCreationService =
      embeddedOptimizeExtension.getApplicationContext().getBean(OnboardingDashboardCreationService.class);
    onboardingDashboardCreationService.createNewDashboardForProcess(DEFAULT_USERNAME, processKey);
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTaskProcess(final String definitionKey) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess(definitionKey);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    return processDefinition;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess(String definitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(
      definitionKey));
  }
}
