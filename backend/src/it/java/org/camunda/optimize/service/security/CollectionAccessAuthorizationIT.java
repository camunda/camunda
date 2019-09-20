/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
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
    final String reportId = createSimpleProcessReportInCollectionAsDefaultUser(collectionId);
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);
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
    final List<EntityDto> entities = collection.getDefinitionDto().getData().getEntities();
    assertThat(entities.size(), is(2));
    assertThat(
      entities.get(0).getCurrentUserRole(),
      is(getExpectedResourceRoleForCollectionRole(accessIdentityRolePairs))
    );
    assertThat(
      entities.get(1).getCurrentUserRole(),
      is(getExpectedResourceRoleForCollectionRole(accessIdentityRolePairs))
    );
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

  private String createSimpleProcessReportInCollectionAsDefaultUser(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createDashboardInCollectionAsDefaultUser(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

}
