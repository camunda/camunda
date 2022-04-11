/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;

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
    List<DefinitionKeyResponseDto> definitionKeys = definitionClient.getDefinitionKeysByTypeAsUser(
      DefinitionType.PROCESS, collectionId, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitionKeys).extracting(DefinitionKeyResponseDto::getKey).containsExactly(DEFAULT_DEFINITION_KEY);
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
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
    List<DefinitionVersionResponseDto> definitionVersions = definitionClient.getDefinitionVersionsByTypeAndKeyAsUser(
      DefinitionType.PROCESS, DEFAULT_DEFINITION_KEY, collectionId, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitionVersions).extracting(DefinitionVersionResponseDto::getVersion).containsExactly("1");
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private void deployAndImportSimpleProcess(final String definitionKey) {
    engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(definitionKey));
    importAllEngineEntitiesFromScratch();

  }
}
