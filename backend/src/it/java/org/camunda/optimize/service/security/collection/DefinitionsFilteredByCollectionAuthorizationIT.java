/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.collection;

import com.google.common.collect.Lists;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionDto;
import org.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.hamcrest.CoreMatchers.is;

public class DefinitionsFilteredByCollectionAuthorizationIT extends AbstractCollectionRoleIT {

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES)
  public void getDefinitionKeys_FilterByCollectionId_ForAuthorizedCollection(final AbstractCollectionRoleIT.IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    List<DefinitionKeyDto> definitionKeys = definitionClient.getDefinitionKeysByTypeAsUser(
      DefinitionType.PROCESS, collectionId, null, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitionKeys).extracting(DefinitionKeyDto::getKey).containsExactly(DEFAULT_DEFINITION_KEY);
  }

  @Test
  public void getDefinitionKeys_FilterByCollectionId_ForUnauthorizedCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionKeysByType(DefinitionType.PROCESS.getId(), collectionId)
      .execute();

    // then
    MatcherAssert.assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES)
  public void getDefinitionVersionByKey_FilterByCollectionId_ForAuthorizedCollection(final AbstractCollectionRoleIT.IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    List<DefinitionVersionDto> definitionVersions = definitionClient.getDefinitionVersionsByTypeAndKeyAsUser(
      DefinitionType.PROCESS, DEFAULT_DEFINITION_KEY, collectionId, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitionVersions).extracting(DefinitionVersionDto::getVersion).containsExactly("1");
  }

  @Test
  public void getDefinitionVersionByKey_FilterByCollectionId_ForUnauthorizedCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDefinitionVersionsByTypeAndKeyRequest(
        DefinitionType.PROCESS.getId(), DEFAULT_DEFINITION_KEY, collectionId
      )
      .execute();

    // then
    MatcherAssert.assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES)
  public void getDefinitionTenantsByTypeKeyAndVersions_FilterByCollectionId_ForAuthorizedCollection(final AbstractCollectionRoleIT.IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    List<TenantResponseDto> tenants = definitionClient.resolveDefinitionTenantsByTypeKeyAndVersionsAsUser(
      DefinitionType.PROCESS, DEFAULT_DEFINITION_KEY, Lists.newArrayList("1"), collectionId, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(tenants).extracting(TenantResponseDto::getId).containsExactly(TENANT_NOT_DEFINED.getId());
  }

  @Test
  public void getDefinitionTenantsByTypeKeyAndVersions_FilterByCollectionId_ForUnauthorizedCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildResolveDefinitionTenantsByTypeKeyAndVersionsRequest(
        DefinitionType.PROCESS.getId(), DEFAULT_DEFINITION_KEY, Lists.newArrayList("1"), collectionId
      )
      .execute();

    // then
    MatcherAssert.assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  private ProcessDefinitionEngineDto deployAndImportSimpleProcess(final String definitionKey) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    final ProcessDefinitionEngineDto processDefinitionEngineDto = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(processModel);
    importAllEngineEntitiesFromScratch();

    return processDefinitionEngineDto;
  }
}
