/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionOptimizeDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DefinitionAuthorizationIT extends AbstractIT {
  public static final String PROCESS_KEY = "aprocess";
  public static final String DECISION_KEY = "aDecision";

  private static final Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantGlobalAccessForAllDefinitions(int definitionResourceType) {
    //given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantGlobalAccessForAllTenants(int definitionResourceType) {
    //given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType);
    deployAndImportDefinition(definitionResourceType, "tenant1");

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(2));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeAllDefinitionAuthorizationsForGroup(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    authorizationClient.revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllResourceAuthorizationsForGroup(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeAllTenantAccessForGroup(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    authorizationClient.revokeAllResourceAuthorizationsForKermit(RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllTenantAccessForGroup(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleDefinitionAuthorizationForGroup(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    authorizationClient.revokeSingleDefinitionAuthorizationsForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleDefinitionAuthorizationsForGroup(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleTenantAuthorizationForGroup(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_TENANT);
    authorizationClient.revokeSingleResourceAuthorizationsForGroup(GROUP_ID, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForGroup(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    authorizationClient.grantSingleResourceAuthorizationsForGroup(GROUP_ID, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeAllResourceAuthorizationsForUser(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    authorizationClient.revokeAllResourceAuthorizationsForKermit(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllDefinitionAuthorizationsForUser(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleDefinitionAuthorizationForUser(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleDefinitionAuthorizationsForUser(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleTenantAuthorizationForUser(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_TENANT);
    authorizationClient.revokeSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForUser(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllTenantAccessForUser(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    authorizationClient.grantSingleResourceAuthorizationForKermit(ALL_RESOURCES_RESOURCE_ID, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeAllTenantAccessForUser(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    authorizationClient.revokeAllResourceAuthorizationsForUser(KERMIT_USER, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAndRevokeSeveralTimes(int definitionResourceType) {
    //given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));

    // when
    authorizationClient.revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));

    // when
    authorizationClient.revokeSingleDefinitionAuthorizationsForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    authorizationClient.grantSingleResourceAuthorizationForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));

    // when
    authorizationClient.revokeAllResourceAuthorizationsForKermit(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));

    // when
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void readAndReadHistoryPermissionsGrandDefinitionAccess(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllDefinitionAuthorizationsForUserWithReadHistoryPermission(
      KERMIT_USER,
      definitionResourceType
    );

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void authorizationForOneGroupIsNotTransferredToOtherGroups(int definitionResourceType) {
    // given
    final String genzoUser = "genzo";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    engineIntegrationExtension.addUser(genzoUser, genzoUser);
    engineIntegrationExtension.grantUserOptimizeAccess(genzoUser);
    engineIntegrationExtension.createGroup("genzoGroup");
    engineIntegrationExtension.addUserToGroup(genzoUser, "genzoGroup");

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> genzosDefinitions = retrieveDefinitionsAsUser(
      definitionResourceType, genzoUser, genzoUser
    );

    // then
    assertThat(genzosDefinitions.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantReadTenantAccessForUser(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, ImmutableList.of(READ_PERMISSION), ALL_RESOURCES_RESOURCE_ID, RESOURCE_TYPE_TENANT
    );

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAuthorizationToSingleDefinitionTransfersToAllVersions(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );

    deployAndImportDefinition(definitionResourceType);
    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  private void deployAndImportDefinition(int definitionResourceType) {
    deployAndImportDefinition(definitionResourceType, null);
  }

  private void deployAndImportDefinition(int definitionResourceType, final String tenantId) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deploySimpleProcessDefinition(PROCESS_KEY, tenantId);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deploySimpleDecisionDefinition(DECISION_KEY, tenantId);
        break;
      default:
        throw new IllegalStateException("Uncovered definitionResourceType: " + definitionResourceType);
    }

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }


  private String getDefinitionKey(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY : DECISION_KEY;
  }

  private <T extends DefinitionOptimizeDto> List<T> retrieveDefinitionsAsKermitUser(int resourceType) {
    return retrieveDefinitionsAsUser(resourceType, KERMIT_USER, KERMIT_USER);
  }

  private <T extends DefinitionOptimizeDto> List<T> retrieveDefinitionsAsUser(final int resourceType,
                                                                              final String userName,
                                                                              final String password) {
    switch (resourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        return (List<T>) retrieveProcessDefinitionsAsUser(userName, password);
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return (List<T>) retrieveDecisionDefinitionsAsUser(userName, password);
      default:
        throw new IllegalArgumentException("Unhandled resourceType: " + resourceType);
    }
  }

  private List<ProcessDefinitionOptimizeDto> retrieveProcessDefinitionsAsUser(String name, String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);
  }

  private List<DecisionDefinitionOptimizeDto> retrieveDecisionDefinitionsAsUser(String name, String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDecisionDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);
  }

  private String deploySimpleProcessDefinition(final String processId, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId).getId();
  }

  private String deploySimpleDecisionDefinition(final String decisionKey, final String tenantId) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    return engineIntegrationExtension.deployDecisionDefinition(modelInstance, tenantId).getId();
  }

}

