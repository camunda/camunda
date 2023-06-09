/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.DefinitionResourceTypeUtil.getResourceTypeByDefinitionType;

public class EngineDefinitionAuthorizationIT extends AbstractIT {
  public static final String PROCESS_KEY = "aProcess";
  public static final String DECISION_KEY = "aDecision";
  private static final String TENANT_ID_1 = "tenant1";
  private static final String TENANT_ID_2 = "tenant2";

  private static Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantGlobalAccessForAllDefinitions(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantGlobalAccessForAllTenants(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType);
    deployAndImportDefinition(definitionResourceType, TENANT_ID_1);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(2);
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
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

    deployAndImportDefinition(definitionResourceType, TENANT_ID_1);
    deployAndImportDefinition(definitionResourceType, TENANT_ID_2);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();
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

    deployAndImportDefinition(definitionResourceType, TENANT_ID_1);
    deployAndImportDefinition(definitionResourceType, TENANT_ID_2);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(2);
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleTenantAuthorizationForGroup(int definitionResourceType) {
    // given
    final String tenantId = TENANT_ID_1;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_TENANT);
    authorizationClient.revokeSingleResourceAuthorizationsForGroup(GROUP_ID, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForGroup(int definitionResourceType) {
    // given
    final String tenantId = TENANT_ID_1;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    authorizationClient.grantSingleResourceAuthorizationsForGroup(GROUP_ID, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllDefinitionAuthorizationsForUser(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleTenantAuthorizationForUser(int definitionResourceType) {
    // given
    final String tenantId = TENANT_ID_1;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_TENANT);
    authorizationClient.revokeSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForUser(int definitionResourceType) {
    // given
    final String tenantId = TENANT_ID_1;
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionResourceType, tenantId);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
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

    deployAndImportDefinition(definitionResourceType, TENANT_ID_1);
    deployAndImportDefinition(definitionResourceType, TENANT_ID_2);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(2);
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

    deployAndImportDefinition(definitionResourceType, TENANT_ID_1);
    deployAndImportDefinition(definitionResourceType, TENANT_ID_2);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAndRevokeSeveralTimes(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployAndImportDefinition(definitionResourceType);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);

    // when
    authorizationClient.revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();

    // when
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);

    // when
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();

    // when
    authorizationClient.grantSingleResourceAuthorizationForKermitGroup(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);

    // when
    authorizationClient.revokeAllResourceAuthorizationsForKermit(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();

    // when
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);

    // when
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).isEmpty();

    // when
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      getDefinitionKey(definitionResourceType),
      definitionResourceType
    );
    definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(1);
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
    List<DefinitionOptimizeResponseDto> genzosDefinitions = retrieveDefinitionsAsUser(
      definitionResourceType, genzoUser, genzoUser
    );

    // then
    assertThat(genzosDefinitions).isEmpty();
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

    deployAndImportDefinition(definitionResourceType, TENANT_ID_1);
    deployAndImportDefinition(definitionResourceType, TENANT_ID_2);

    // when
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(2);
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
    List<DefinitionOptimizeResponseDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions).hasSize(2);
  }

  @ParameterizedTest(name = "No access to unauthorized definition of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

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
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest(name = "If no single tenant is authorized do not allow access to definition of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

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
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest(name = "On partial tenant authorization only authorized tenants are returned of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitionByTypeAndKey(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final String tenant2 = TENANT_ID_2;
    engineIntegrationExtension.createTenant(tenant2);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant2, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);
    deployAndImportDefinition(definitionType, definitionKey, tenant2);

    // when
    final DefinitionResponseDto definition = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionByTypeAndKeyRequest(definitionType.getId(), definitionKey)
      .execute(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definition).isNotNull();
    assertThat(definition.getKey()).isEqualTo(definitionKey);
    assertThat(definition.getTenants())
      .hasSize(1)
      .extracting(TenantDto::getId)
      .containsExactly(tenant2);
  }

  @ParameterizedTest(name = "Unauthorized definition of type {0} is not in definitions result")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitions(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey, null);

    // when
    final List<DefinitionResponseDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions).isEmpty();
  }

  @ParameterizedTest(name = "Unauthorized single tenant definition of type {0} is not in definitions result")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitions(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);

    // when
    final List<DefinitionResponseDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions).isEmpty();
  }

  @ParameterizedTest(name = "On partial tenant authorization only authorized tenants are returned of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitions(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final String tenant2 = TENANT_ID_2;
    engineIntegrationExtension.createTenant(tenant2);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant2, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);
    deployAndImportDefinition(definitionType, definitionKey, tenant2);

    // when
    final List<DefinitionResponseDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions)
      .flatExtracting(DefinitionResponseDto::getTenants)
      .extracting(TenantDto::getId)
      .containsExactly(tenant2);
  }

  @ParameterizedTest(name = "Unauthorized definition of type {0} is not in definitions result")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitionKeysByType(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey, null);

    // when
    final List<DefinitionKeyResponseDto> definitionKeys = definitionClient.getDefinitionKeysByTypeAsUser(
      definitionType, null, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitionKeys).isEmpty();
  }

  @ParameterizedTest(name = "Unauthorized single tenant definition of type {0} is not in definitionKeys result")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitionKeysByType(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);

    // when
    final List<DefinitionKeyResponseDto> definitionKeys = definitionClient.getDefinitionKeysByTypeAsUser(
      definitionType, null, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitionKeys).isEmpty();
  }

  @ParameterizedTest(
    name = "On partial tenant authorization for the same definition of type {0} the key is still returned"
  )
  @EnumSource(DefinitionType.class)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitionKeysByType(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final String tenant2 = TENANT_ID_2;
    engineIntegrationExtension.createTenant(tenant2);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    // access to tenant1 is revoked
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant2, RESOURCE_TYPE_TENANT);

    // definition exists for both tenants
    deployAndImportDefinition(definitionType, definitionKey, tenant1);
    deployAndImportDefinition(definitionType, definitionKey, tenant2);

    // when I get the definition keys
    final List<DefinitionKeyResponseDto> definitionKeys = definitionClient.getDefinitionKeysByTypeAsUser(
      definitionType, null, KERMIT_USER, KERMIT_USER
    );

    // then the key is still available as there is access to at least one tenant
    assertThat(definitionKeys).extracting(DefinitionKeyResponseDto::getKey).containsExactly(definitionKey);
  }

  @ParameterizedTest(name = "Unauthorized definition of type {0} is not accessible")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitionVersionsByKeyByType(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey, null);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDefinitionVersionsByTypeAndKeyRequest(definitionType.getId(), definitionKey)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest(name = "Unauthorized single tenant definition of type {0} is not accessible")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitionVersionsByKeyByType(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDefinitionVersionsByTypeAndKeyRequest(definitionType.getId(), definitionKey)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitionVersionsByKeyByType(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    engineIntegrationExtension.createTenant(TENANT_ID_1);
    engineIntegrationExtension.createTenant(TENANT_ID_2);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    // access to tenant1 is revoked
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(TENANT_ID_1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(TENANT_ID_2, RESOURCE_TYPE_TENANT);

    // definition exists for both tenants but only on tenant 1 there is a version 2
    deployAndImportDefinition(definitionType, definitionKey, TENANT_ID_1);
    deployAndImportDefinition(definitionType, definitionKey, TENANT_ID_1);
    deployAndImportDefinition(definitionType, definitionKey, TENANT_ID_2);

    // when I get the definition keys
    final List<DefinitionVersionResponseDto> definitionKeys = definitionClient.getDefinitionVersionsByTypeAndKeyAsUser(
      definitionType, definitionKey, null, KERMIT_USER, KERMIT_USER
    );

    // then only version 1 as available on the authorized tenant is returned
    assertThat(definitionKeys).extracting(DefinitionVersionResponseDto::getVersion).containsExactly("1");
  }

  @ParameterizedTest(name = "Unauthorized definition of type {0} is not accessible")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitionTenantsByTypeForMultipleKeyAndVersions(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey, null);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildResolveDefinitionTenantsByTypeMultipleKeysAndVersionsRequest(
        definitionType.getId(),
        new MultiDefinitionTenantsRequestDto(List.of(createDefinitionDto(definitionKey)))
      )
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest(name = "Unauthorized single tenant definition of type {0} is not accessible")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitionTenantsByTypeForMultipleKeyAndVersions(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    engineIntegrationExtension.createTenant(TENANT_ID_1);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(TENANT_ID_1, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, TENANT_ID_1);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildResolveDefinitionTenantsByTypeMultipleKeysAndVersionsRequest(
        definitionType.getId(),
        new MultiDefinitionTenantsRequestDto(List.of(createDefinitionDto(
          definitionKey)))
      )
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitionTenantsByTypeForMultipleKeyAndVersions(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    engineIntegrationExtension.createTenant(TENANT_ID_1);
    engineIntegrationExtension.createTenant(TENANT_ID_2);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    // access to tenant1 is revoked
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(TENANT_ID_1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(TENANT_ID_2, RESOURCE_TYPE_TENANT);

    // definition exists for both tenants
    deployAndImportDefinition(definitionType, definitionKey, TENANT_ID_1);
    deployAndImportDefinition(definitionType, definitionKey, TENANT_ID_2);

    // when I get the definition tenants
    final List<DefinitionWithTenantsResponseDto> definitionsWithTenants = definitionClient
      .resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        definitionType,
        new MultiDefinitionTenantsRequestDto(List.of(createDefinitionDto(definitionKey))),
        KERMIT_USER, KERMIT_USER
      );

    // then only the authorized tenant2 is returned
    assertThat(definitionsWithTenants)
      .extracting(DefinitionWithTenantsResponseDto::getTenants)
      .containsExactly(
        Collections.singletonList(new TenantResponseDto(TENANT_ID_2, TENANT_ID_2))
      );
  }

  @ParameterizedTest(name = "Unauthorized definition of type {0} is not in definitions grouped by tenant result")
  @EnumSource(DefinitionType.class)
  public void revokeDefinitionAuthorizationsUser_getDefinitionsGroupByTenant(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey, null);

    // when
    final List<TenantWithDefinitionsResponseDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant).isEmpty();
  }

  @ParameterizedTest(name = "Unauthorized single tenant definition of type {0} is not in definitions result")
  @EnumSource(DefinitionType.class)
  public void revokeTenantAuthorizationsUser_getDefinitionsGroupByTenant(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);

    // when
    final List<TenantWithDefinitionsResponseDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant).isEmpty();
  }

  @ParameterizedTest(name = "On partial definition authorization only authorized definitions are returned of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeJustOneDefinitionAuthorizationsUser_getDefinitionsGroupByTenant(final DefinitionType definitionType) {
    // given
    final String definitionKey1 = "key";
    final String definitionKey2 = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey1, engineResourceType);
    authorizationClient.grantSingleResourceAuthorizationForKermit(definitionKey2, engineResourceType);

    deployAndImportDefinition(definitionType, definitionKey1, tenant1);
    deployAndImportDefinition(definitionType, definitionKey2, tenant1);

    // when
    final List<TenantWithDefinitionsResponseDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant)
      .flatExtracting(TenantWithDefinitionsResponseDto::getDefinitions)
      .extracting(SimpleDefinitionDto::getKey)
      .containsExactly(definitionKey2);
  }

  @ParameterizedTest(name = "On partial tenant authorization for the same definition authorized tenants are returned " +
    "of type {0}")
  @EnumSource(DefinitionType.class)
  public void revokeJustOneTenantAuthorizationsUser_getDefinitionsGroupByTenant(final DefinitionType definitionType) {
    // given
    final String definitionKey = "key";
    final String tenant1 = TENANT_ID_1;
    engineIntegrationExtension.createTenant(tenant1);
    final String tenant2 = TENANT_ID_2;
    engineIntegrationExtension.createTenant(tenant2);
    final int engineResourceType = getResourceTypeByDefinitionType(definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.addGlobalAuthorizationForResource(engineResourceType);
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(tenant1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationForKermit(tenant2, RESOURCE_TYPE_TENANT);

    deployAndImportDefinition(definitionType, definitionKey, tenant1);
    deployAndImportDefinition(definitionType, definitionKey, tenant2);

    // when
    final List<TenantWithDefinitionsResponseDto> definitionsGroupedByTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionsGroupedByTenant()
      .executeAndReturnList(TenantWithDefinitionsResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitionsGroupedByTenant)
      .extracting(TenantWithDefinitionsResponseDto::getId)
      .containsExactly(tenant2);
    assertThat(definitionsGroupedByTenant)
      .flatExtracting(TenantWithDefinitionsResponseDto::getDefinitions)
      .extracting(SimpleDefinitionDto::getKey)
      .containsExactly(definitionKey);
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

    importAllEngineEntitiesFromScratch();
  }

  private String getDefinitionKey(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY : DECISION_KEY;
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> retrieveDefinitionsAsKermitUser(int resourceType) {
    return retrieveDefinitionsAsUser(resourceType, KERMIT_USER, KERMIT_USER);
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> retrieveDefinitionsAsUser(final int resourceType,
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
    importAllEngineEntitiesFromScratch();
    return definitionId;
  }

  private String deploySimpleProcessDefinition(final String processId, String tenantId) {
    BpmnModelInstance modelInstance = getSimpleBpmnDiagram(processId);
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId).getId();
  }

  private String deploySimpleDecisionDefinition(final String decisionKey, final String tenantId) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    return engineIntegrationExtension.deployDecisionDefinition(modelInstance, tenantId).getId();
  }

  private static MultiDefinitionTenantsRequestDto.DefinitionDto createDefinitionDto(final String definitionKey) {
    final MultiDefinitionTenantsRequestDto.DefinitionDto definitionDto = new MultiDefinitionTenantsRequestDto.DefinitionDto();
    definitionDto.setKey(definitionKey);
    return definitionDto;
  }

}

