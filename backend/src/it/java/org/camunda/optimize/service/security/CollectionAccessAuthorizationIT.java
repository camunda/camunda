/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedResolvedCollectionDefinitionDto;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class CollectionAccessAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  public void creatorCanAccessCollection() {
    // given
    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    AuthorizedResolvedCollectionDefinitionDto collection = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(AuthorizedResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(collection.getDefinitionDto().getId(), is(collectionId));
    assertThat(collection.getCurrentUserRole(), is(RoleType.MANAGER));
  }

  @Test
  @Parameters(method = ACCESS_IDENTITY_ROLES)
  public void identityIsGrantedAccessByCollectionRole(final IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    AuthorizedResolvedCollectionDefinitionDto collection = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetCollectionRequest(collectionId)
      .execute(AuthorizedResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(collection.getDefinitionDto().getId(), is(collectionId));
    assertThat(collection.getCurrentUserRole(), is(accessIdentityRolePairs.roleType));
  }

  @Test
  public void userIsNotGrantedAccessDueMissingRole() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetCollectionRequest(collectionId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = ACCESS_IDENTITY_ROLES)
  public void identityCanListAuthorizedCollectionsByCollectionRole(final IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    List<AuthorizedResolvedCollectionDefinitionDto> authorizedResolvedCollections = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllCollectionsRequest()
      .executeAndReturnList(AuthorizedResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(authorizedResolvedCollections.size(), is(1));
    assertThat(authorizedResolvedCollections.get(0).getDefinitionDto().getId(), is(collectionId));
    assertThat(authorizedResolvedCollections.get(0).getCurrentUserRole(), is(accessIdentityRolePairs.roleType));
  }

  @Test
  public void userCanNotListUnauthorizedCollections() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    createNewCollectionAsDefaultUser();

    // when
    List<AuthorizedResolvedCollectionDefinitionDto> authorizedResolvedCollections = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllCollectionsRequest()
      .executeAndReturnList(AuthorizedResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(authorizedResolvedCollections.size(), is(0));
  }

}
