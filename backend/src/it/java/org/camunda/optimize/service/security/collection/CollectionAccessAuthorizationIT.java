/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.collection;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionAccessAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  public void creatorCanAccessCollection() {
    // given
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    AuthorizedCollectionDefinitionRestDto collection = collectionClient.getAuthorizedCollectionById(collectionId);

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

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    createSimpleProcessReportInCollectionAsDefaultUser(collectionId);
    createDashboardInCollectionAsDefaultUser(collectionId);
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    AuthorizedCollectionDefinitionRestDto collection = collectionClient.getAuthorizedCollectionById(
      collectionId,
      KERMIT_USER,
      KERMIT_USER
    );

    final List<EntityDto> entities = collectionClient.getEntitiesForCollection(collectionId, KERMIT_USER, KERMIT_USER);
    // then
    assertThat(collection.getDefinitionDto().getId(), is(collectionId));
    assertThat(collection.getCurrentUserRole(), is(accessIdentityRolePairs.roleType));

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
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when + then
    collectionClient.getAuthorizedCollectionById(collectionId, KERMIT_USER, KERMIT_USER);
  }

  @Test
  public void userIsNotGrantedAccessDueMissingRole() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetCollectionRequest(collectionId)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  private String createSimpleProcessReportInCollectionAsDefaultUser(final String collectionId) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createCombinedReport(collectionId, new ArrayList<>());
  }

  private String createDashboardInCollectionAsDefaultUser(final String collectionId) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    return dashboardClient.createDashboard(collectionId, new ArrayList<>());
  }

}
