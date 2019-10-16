/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedResolvedCollectionDefinitionDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionAccessAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  public void creatorCanAccessCollection() {
    // given
    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    AuthorizedResolvedCollectionDefinitionDto collection = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(AuthorizedResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(collection.getDefinitionDto().getId(), is(collectionId));
    assertThat(collection.getCurrentUserRole(), is(RoleType.MANAGER));
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES)
  public void identityIsGrantedAccessByCollectionRole(final IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    createSimpleProcessReportInCollectionAsDefaultUser(collectionId);
    createDashboardInCollectionAsDefaultUser(collectionId);
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    AuthorizedResolvedCollectionDefinitionDto collection = embeddedOptimizeExtensionRule
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
  public void userIsGrantedAccessAsSuperUser() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtensionRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetCollectionRequest(collectionId)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void userIsNotGrantedAccessDueMissingRole() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetCollectionRequest(collectionId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  private String createSimpleProcessReportInCollectionAsDefaultUser(final String collectionId) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createDashboardInCollectionAsDefaultUser(final String collectionId) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

}
