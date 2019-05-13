/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionOptimizeDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
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
  public void grantGlobalAccessForAllDefinitions(int definitionResourceType) {
    //given
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForResource(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantGlobalAccessForAllTenants(int definitionResourceType) {
    //given
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForResource(definitionResourceType);
    addGlobalAuthorizationForResource(RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType);
    deployAndImportDefinition(definitionResourceType, "tenant1");

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(2));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeAllDefinitionAuthorizationsForGroup(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    addGlobalAuthorizationForResource(definitionResourceType);
    revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAllResourceAuthorizationsForGroup(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeAllTenantAccessForGroup(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantSingleResourceAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    createTenantGroupAuthorization(
      GROUP_ID, ImmutableList.of(ALL_PERMISSION), ALL_RESOURCES_RESOURCE_ID, AUTHORIZATION_TYPE_REVOKE
    );

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAllTenantAccessForGroup(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantSingleResourceAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    createTenantGroupAuthorization(
      GROUP_ID, ImmutableList.of(ALL_PERMISSION), ALL_RESOURCES_RESOURCE_ID, AUTHORIZATION_TYPE_GRANT
    );

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeSingleDefinitionAuthorizationForGroup(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    revokeSingleDefinitionAuthorizationsForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantSingleDefinitionAuthorizationsForGroup(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantSingleResourceAuthorizationForKermitGroup(getDefinitionKey(definitionResourceType), definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeSingleTenantAuthorizationForGroup(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_TENANT);
    revokeSingleResourceAuthorizationsForGroup(GROUP_ID, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantSingleTenantAuthorizationsForGroup(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    grantSingleResourceAuthorizationsForGroup(GROUP_ID, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeAllResourceAuthorizationsForUser(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForResource(definitionResourceType);
    revokeAllResourceAuthorizationsForKermit(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAllDefinitionAuthorizationsForUser(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllResourceAuthorizationsForKermit(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeSingleDefinitionAuthorizationForUser(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllResourceAuthorizationsForKermit(definitionResourceType);
    revokeSingleResourceAuthorizationsForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantSingleResourceAuthorizationsForUser(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleResourceAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeSingleTenantAuthorizationForUser(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    addKermitUserAndGrantAccessToOptimize();
    grantAllResourceAuthorizationsForKermit(definitionResourceType);
    grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_TENANT);
    revokeSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantSingleTenantAuthorizationsForUser(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    addKermitUserAndGrantAccessToOptimize();
    grantAllResourceAuthorizationsForKermit(definitionResourceType);
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAllTenantAccessForUser(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleResourceAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    createTenantUserAuthorization(
      KERMIT_USER, ImmutableList.of(ALL_PERMISSION), ALL_RESOURCES_RESOURCE_ID, AUTHORIZATION_TYPE_GRANT
    );

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  @Test
  @Parameters(method = "definitionType")
  public void revokeAllTenantAccessForUser(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleResourceAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    createTenantUserAuthorization(
      KERMIT_USER, ImmutableList.of(ALL_PERMISSION), ALL_RESOURCES_RESOURCE_ID, AUTHORIZATION_TYPE_REVOKE
    );

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAndRevokeSeveralTimes(int definitionResourceType) {
    //given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    addGlobalAuthorizationForResource(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));

    // when
    revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
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
    grantSingleResourceAuthorizationForKermitGroup(getDefinitionKey(definitionResourceType), definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));

    // when
    revokeAllResourceAuthorizationsForKermit(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantAllResourceAuthorizationsForKermit(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));

    // when
    revokeSingleResourceAuthorizationsForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantSingleResourceAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void authorizationForOneGroupIsNotTransferredToOtherGroups(int definitionResourceType) {
    // given
    final String genzoUser = "genzo";
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    engineRule.addUser(genzoUser, genzoUser);
    engineRule.grantUserOptimizeAccess(genzoUser);
    engineRule.createGroup("genzoGroup", "Group", "foo");
    engineRule.addUserToGroup(genzoUser, "genzoGroup");

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> genzosDefinitions = retrieveDefinitionsAsUser(
      definitionResourceType, genzoUser, genzoUser
    );

    // then
    assertThat(genzosDefinitions.size(), is(0));
  }

  @Test
  @Parameters(method = "definitionType")
  public void readAndReadHistoryPermissionsGrandDefinitionAccess(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllDefinitionAuthorizationsForUserWithReadPermission(KERMIT_USER, definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantReadTenantAccessForUser(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleResourceAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);
    createTenantUserAuthorization(
      KERMIT_USER, ImmutableList.of(READ_PERMISSION), ALL_RESOURCES_RESOURCE_ID, AUTHORIZATION_TYPE_GRANT
    );

    deployAndImportDefinition(definitionResourceType, "tenant1");
    deployAndImportDefinition(definitionResourceType, "tenant2");

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  @Test
  @Parameters(method = "definitionType")
  public void grantAuthorizationToSingleDefinitionTransfersToAllVersions(int definitionResourceType) {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleResourceAuthorizationForKermit(getDefinitionKey(definitionResourceType), definitionResourceType);

    deployAndImportDefinition(definitionResourceType);
    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  private void createTenantUserAuthorization(final String tenantUser,
                                             final ImmutableList<String> permissions,
                                             final String resourceIdId,
                                             int type) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_TENANT);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(resourceIdId);
    authorizationDto.setType(type);
    authorizationDto.setUserId(tenantUser);
    engineRule.createAuthorization(authorizationDto);
  }

  private void createTenantGroupAuthorization(final String groupId,
                                              final ImmutableList<String> permissions,
                                              final String resourceIdId,
                                              int type) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_TENANT);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(resourceIdId);
    authorizationDto.setType(type);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
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

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
  }

  private void addGlobalAuthorizationForResource(int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantAllResourceAuthorizationsForKermitGroup(int resourceType) {
    grantAllResourceAuthorizationsForGroup(GROUP_ID, resourceType);
  }

  private void grantSingleResourceAuthorizationForKermitGroup(String resourceId, int resourceType) {
    grantSingleResourceAuthorizationsForGroup(GROUP_ID, resourceId, resourceType);
  }

  private void revokeAllDefinitionAuthorizationsForKermitGroup(int resourceType) {
    revokeAllDefinitionAuthorizationsForGroup(GROUP_ID, resourceType);
  }

  private void revokeSingleDefinitionAuthorizationsForKermitGroup(String resourceID, int reourceType) {
    revokeSingleResourceAuthorizationsForGroup(GROUP_ID, resourceID, reourceType);
  }

  private void grantAllResourceAuthorizationsForGroup(String groupId, int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantSingleResourceAuthorizationsForGroup(String groupId,
                                                         String resourceId,
                                                         int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(resourceId);
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

  private void revokeSingleResourceAuthorizationsForGroup(String groupId,
                                                          String resourceId,
                                                          int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantAllResourceAuthorizationsForKermit(int resourceType) {
    grantAllDefinitionAuthorizationsForUser(KERMIT_USER, resourceType);
  }

  private void grantSingleResourceAuthorizationForKermit(String resourceId, int resourceType) {
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, resourceId, resourceType);
  }

  private void revokeAllResourceAuthorizationsForKermit(int resourceType) {
    revokeAllResourceAuthorizationsForUser(KERMIT_USER, resourceType);
  }

  private void revokeSingleResourceAuthorizationsForKermit(String resourceId, int resourceType) {
    revokeSingleResourceAuthorizationsForUser(KERMIT_USER, resourceId, resourceType);
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

  private void grantSingleResourceAuthorizationsForUser(String userId,
                                                        String resourceId,
                                                        int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(resourceId);
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

  private void revokeAllResourceAuthorizationsForUser(String userId, int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeSingleResourceAuthorizationsForUser(String userId,
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

  private String deploySimpleProcessDefinition(final String processId, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance, tenantId).getId();
  }

  private String deploySimpleDecisionDefinition(final String decisionKey, final String tenantId) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    return engineRule.deployDecisionDefinition(modelInstance, tenantId).getId();
  }

  private void createKermitGroupAndAddKermitToThatGroup() {
    engineRule.createGroup(GROUP_ID, "Group", "foo");
    engineRule.addUserToGroup(KERMIT_USER, GROUP_ID);
  }

}

