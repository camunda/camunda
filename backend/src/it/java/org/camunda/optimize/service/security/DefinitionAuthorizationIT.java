/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.KeyDefinitionOptimizeDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.util.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class DefinitionAuthorizationIT {

  public static final String GROUP_ID = "kermitGroup";
  public static final String PROCESS_KEY = "aprocess";
  public static final String DECISION_KEY = "aDecision";
  public static final String KERMIT_USER = "kermit";

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  @Parameters(method = "definitionType")
  public void grantGlobalAccessForAllDefinitions(int definitionResourceType) throws IOException {
    //given
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForAllDefinitions(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    //when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeAllDefinitionAuthorizationsForGroup(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    addGlobalAuthorizationForAllDefinitions(definitionResourceType);
    revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAllDefinitionAuthorizationsForGroup(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeSingleDefinitionAuthorizationForGroup(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);
    revokeSingleDefinitionAuthorizationsForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantSingleDefinitionAuthorizationsForGroup(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantSingleDefinitionAuthorizationForKermitGroup(getDefinitionKey(definitionResourceType), definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeAllDefinitionAuthorizationsForUser(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForAllDefinitions(definitionResourceType);
    revokeAllDefinitionAuthorizationsForKermit(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAllDefinitionAuthorizationsForUser(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllDefinitionAuthorizationsForKermit(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeSingleDefinitionAuthorizationForUser(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllDefinitionAuthorizationsForKermit(definitionResourceType);
    revokeSingleDefinitionAuthorizationsForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantSingleDefinitionAuthorizationsForUser(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleDefinitionAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAndRevokeSeveralTimes(int definitionResourceType) throws IOException {
    //given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    addGlobalAuthorizationForAllDefinitions(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    //when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));

    // when
    revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));

    // when
    revokeSingleDefinitionAuthorizationsForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantSingleDefinitionAuthorizationForKermitGroup(getDefinitionKey(definitionResourceType), definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));

    // when
    revokeAllDefinitionAuthorizationsForKermit(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantAllDefinitionAuthorizationsForKermit(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));

    // when
    revokeSingleDefinitionAuthorizationsForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantSingleDefinitionAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }


  @Test
  @Parameters(method = "definitionType")
  public void authorizationForOneGroupIsNotTransferredToOtherGroups(int definitionResourceType) throws Exception {
    // given
    final String genzoUser = "genzo";
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);
    engineRule.addUser(genzoUser, genzoUser);
    engineRule.grantUserOptimizeAccess(genzoUser);
    engineRule.createGroup("genzoGroup", "Group", "foo");
    engineRule.addUserToGroup(genzoUser, "genzoGroup");

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> genzosDefinitions = retrieveDefinitionsAsUser(
      definitionResourceType, genzoUser, genzoUser
    );

    // then
    assertThat(genzosDefinitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void readAndReadHistoryPermissionsGrandDefinitionAccess(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllDefinitionAuthorizationsForUserWithReadPermission(KERMIT_USER, definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAuthorizationToSingleDefinitionTransfersToAllVersions(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleDefinitionAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);

    deployAndImportDefinition(definitionResourceType);
    deployAndImportDefinition(definitionResourceType);

    // when
    List<KeyDefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  private void deployAndImportDefinition(int definitionResourceType) throws IOException {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deploySimpleProcessDefinition(PROCESS_KEY);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deploySimpleDecisionDefinition(DECISION_KEY);
        break;
      default:
        throw new IllegalStateException("Uncovered definitionResourceType: " + definitionResourceType);
    }

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
  }

  private void addGlobalAuthorizationForAllDefinitions(int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantAllDefinitionAuthorizationsForKermitGroup(int definitionResourceType) {
    grantAllDefinitionAuthorizationsForGroup(GROUP_ID, definitionResourceType);
  }

  private void grantSingleDefinitionAuthorizationForKermitGroup(String definitionKey, int definitionResourceType) {
    grantSingleDefinitionAuthorizationsForGroup(GROUP_ID, definitionKey, definitionResourceType);
  }

  private void revokeAllDefinitionAuthorizationsForKermitGroup(int definitionResourceType) {
    revokeAllDefinitionAuthorizationsForGroup(GROUP_ID, definitionResourceType);
  }

  private void revokeSingleDefinitionAuthorizationsForKermitGroup(String definitionKey, int definitionResourceType) {
    revokeSingleDefinitionAuthorizationsForGroup(GROUP_ID, definitionKey, definitionResourceType);
  }

  private void grantAllDefinitionAuthorizationsForGroup(String groupId, int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantSingleDefinitionAuthorizationsForGroup(String groupId,
                                                           String definitionKey,
                                                           int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeAllDefinitionAuthorizationsForGroup(String groupId, int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeSingleDefinitionAuthorizationsForGroup(String groupId,
                                                            String definitionKey,
                                                            int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantAllDefinitionAuthorizationsForKermit(int definitionResourceType) {
    grantAllDefinitionAuthorizationsForUser(KERMIT_USER, definitionResourceType);
  }

  private void grantSingleDefinitionAuthorizationForKermit(String definitionKey, int definitionResourceType) {
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, definitionKey, definitionResourceType);
  }

  private void revokeAllDefinitionAuthorizationsForKermit(int definitionResourceType) {
    revokeAllDefinitionAuthorizationsForUser(KERMIT_USER, definitionResourceType);
  }

  private void revokeSingleDefinitionAuthorizationsForKermit(String definitionKey, int definitionResourceType) {
    revokeSingleDefinitionAuthorizationsForUser(KERMIT_USER, definitionKey, definitionResourceType);
  }

  private void grantAllDefinitionAuthorizationsForUser(String userId, int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantSingleDefinitionAuthorizationsForUser(String userId,
                                                          String definitionKey,
                                                          int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantAllDefinitionAuthorizationsForUserWithReadPermission(String userId, int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    List<String> permissions = new ArrayList<>();
    permissions.add(READ_HISTORY_PERMISSION);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeAllDefinitionAuthorizationsForUser(String userId, int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeSingleDefinitionAuthorizationsForUser(String userId,
                                                           String definitionKey,
                                                           int definitionResourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(definitionResourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void addKermitUserAndGrantAccessToOptimize() {
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
  }

  private String getDefinitionKey(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY : DECISION_KEY;
  }

  private <T extends KeyDefinitionOptimizeDto> List<T> retrieveDefinitionsAsKermitUser(int resourceType) {
    return retrieveDefinitionsAsUser(resourceType, KERMIT_USER, KERMIT_USER);
  }

  private <T extends KeyDefinitionOptimizeDto> List<T> retrieveDefinitionsAsUser(final int resourceType,
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
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);
  }

  private List<DecisionDefinitionOptimizeDto> retrieveDecisionDefinitionsAsUser(String name, String password) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);
  }

  private String deploySimpleProcessDefinition(final String processId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetId(modelInstance);
  }

  private String deploySimpleDecisionDefinition(final String decisionKey) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    return engineRule.deployDecisionDefinition(modelInstance).getId();
  }

  private void createKermitGroupAndAddKermitToThatGroup() {
    engineRule.createGroup(GROUP_ID, "Group", "foo");
    engineRule.addUserToGroup(KERMIT_USER, GROUP_ID);
  }

}

