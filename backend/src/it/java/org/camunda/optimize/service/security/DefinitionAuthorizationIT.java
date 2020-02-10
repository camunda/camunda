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
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class DefinitionAuthorizationIT extends AbstractIT {
  public static final String PROCESS_KEY = "aProcess";
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
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
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
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
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

  @ParameterizedTest(name = "No access to unauthorized definition of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey, null);

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionByTypeAndKeyRequest(definitionType.getId(), definitionKey)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void revokeDefinitionAuthorizationsUser_getDefinitionByTypeAndKey_EventBased() {
    //given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    addSimpleEventProcessToElasticsearch(definitionKey);

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionByTypeAndKeyRequest(DefinitionType.PROCESS.getId(), definitionKey)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  @ParameterizedTest(name = "If no single tenant is authorized do not allow access to definition of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionByTypeAndKeyRequest(definitionType.getId(), definitionKey)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @ParameterizedTest(name = "On partial tenant authorization only authorized tenants are returned of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    final String tenant2 = "tenant2";
    engineIntegrationExtension.createTenant(tenant2);
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant2, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);
    deployAndImportDefinition(definitionType, definitionKey, tenant2);

    // when
    final DefinitionWithTenantsDto definition = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionByTypeAndKeyRequest(definitionType.getId(), definitionKey)
      .execute(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definition, is(is(notNullValue())));
    assertThat(definition.getKey(), is(definitionKey));
    assertThat(definition.getTenants().size(), is(1));
    assertThat(definition.getTenants().get(0).getId(), is(tenant2));
  }

  @ParameterizedTest(name = "Unauthorized definition of type {0} is not in definitions result")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitions(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey, null);

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  public void revokeDefinitionAuthorizationsUser_getDefinitions_EventBased() {
    //given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    addSimpleEventProcessToElasticsearch(definitionKey);

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.size(), is(1));
  }

  @ParameterizedTest(name = "Unauthorized single tenant definition of type {0} is not in definitions result")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitions(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.size(), is(0));
  }

  @ParameterizedTest(name = "On partial tenant authorization only authorized tenants are returned of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitions(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    final String tenant2 = "tenant2";
    engineIntegrationExtension.createTenant(tenant2);
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant2, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);
    deployAndImportDefinition(definitionType, definitionKey, tenant2);

    // when
    final List<DefinitionWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getTenants().size(), is(1));
    assertThat(definitions.get(0).getTenants().get(0).getId(), is(tenant2));
  }

  @ParameterizedTest(name = "Unauthorized definition of type {0} is not in definitions grouped by tenant result")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitionsGroupByTenant(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey, null);

    // when
    final List<TenantWithDefinitionsDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant.size(), is(0));
  }

  @Test
  public void revokeDefinitionAuthorizationsUser_getDefinitionsGroupByTenant_EventBased() {
    //given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    addSimpleEventProcessToElasticsearch(definitionKey);

    // when
    final List<TenantWithDefinitionsDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant.size(), is(1));
  }

  @ParameterizedTest(name = "Unauthorized single tenant definition of type {0} is not in definitions result")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitionsGroupByTenant(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);

    // when
    final List<TenantWithDefinitionsDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant.size(), is(0));
  }

  @ParameterizedTest(name = "On partial definition authorization only authorized definitions are returned of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeJustOneDefinitionAuthorizationsUser_getDefinitionsGroupByTenant(final DefinitionType definitionType) {
    //given
    final String definitionKey1 = "key";
    final String definitionKey2 = "key";
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey1, engineResourceType);
    authorizationClient.grantSingleResourceAuthorizationForKermit(definitionKey2, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey1, tenant1);
    deployAndImportDefinition(definitionType, definitionKey2, tenant1);

    // when
    final List<TenantWithDefinitionsDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant.size(), is(1));
    assertThat(definitionsGroupedByTenant.get(0).getDefinitions().size(), is(1));
    assertThat(definitionsGroupedByTenant.get(0).getDefinitions().get(0).getKey(), is(definitionKey2));
  }

  @Test
  public void revokeJustOneDefinitionAuthorizationsUser_getDefinitionsGroupByTenant_EventBased() {
    //given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey1, RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationForKermit(definitionKey2, RESOURCE_TYPE_PROCESS_DEFINITION);

    addSimpleEventProcessToElasticsearch(definitionKey1);
    addSimpleEventProcessToElasticsearch(definitionKey2);

    // when
    final List<TenantWithDefinitionsDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant.size(), is(1));
    assertThat(definitionsGroupedByTenant.get(0).getDefinitions())
      .extracting(SimpleDefinitionDto::getKey)
      .containsExactlyInAnyOrder(
        definitionKey1,
        definitionKey2
      );
  }

  @ParameterizedTest(name = "On partial tenant authorization for the same definition authorized tenants are returned " +
    "of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitionsGroupByTenant(final DefinitionType definitionType) {
    //given
    final String definitionKey = "key";
    final String tenant1 = "tenant1";
    engineIntegrationExtension.createTenant(tenant1);
    final String tenant2 = "tenant2";
    engineIntegrationExtension.createTenant(tenant2);
    final int engineResourceType = getEngineResourceType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant2, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);
    deployAndImportDefinition(definitionType, definitionKey, tenant2);

    // when
    final List<TenantWithDefinitionsDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant.size(), is(1));
    assertThat(definitionsGroupedByTenant.get(0).getId(), is(tenant2));
    assertThat(definitionsGroupedByTenant.get(0).getDefinitions().size(), is(1));
    assertThat(definitionsGroupedByTenant.get(0).getDefinitions().get(0).getKey(), is(definitionKey));
  }

  private int getEngineResourceType(final DefinitionType definitionType) {
    return DefinitionType.PROCESS.equals(definitionType)
      ? RESOURCE_TYPE_PROCESS_DEFINITION
      : RESOURCE_TYPE_DECISION_DEFINITION;
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
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  private List<DecisionDefinitionOptimizeDto> retrieveDecisionDefinitionsAsUser(String name, String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDecisionDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  private String deployAndImportDefinition(final DefinitionType type, final String key, String tenantId) {
    String definitionId = null;
    switch (type) {
      case PROCESS:
        definitionId = deploySimpleProcessDefinition(key, tenantId);
        break;
      case DECISION:
        definitionId = deploySimpleDecisionDefinition(key, tenantId);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported type: " + type);
    }
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return definitionId;
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

  private EventProcessDefinitionDto addSimpleEventProcessToElasticsearch(final String key) {
    final EventProcessDefinitionDto eventProcessDefinitionDto = EventProcessDefinitionDto.eventProcessBuilder()
      .id(key + "- 1")
      .key(key)
      .name("eventProcessName")
      .version("1")
      .bpmn20Xml(key + "1")
      .flowNodeNames(Collections.emptyMap())
      .userTaskNames(Collections.emptyMap())
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      EVENT_PROCESS_DEFINITION_INDEX_NAME,
      eventProcessDefinitionDto.getId(),
      eventProcessDefinitionDto
    );
    return eventProcessDefinitionDto;
  }
}

