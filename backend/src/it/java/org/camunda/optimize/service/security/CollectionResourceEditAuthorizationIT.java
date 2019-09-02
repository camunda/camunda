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
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class CollectionResourceEditAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  @Parameters(method = EDIT_IDENITY_ROLES_AND_RESOURCE_SCENARIOS)
  public void editorIdentityIsGrantedAddResourceByCollectionRole(final IdentityRoleAndResource roleAndResourcePair) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      roleAndResourcePair.identityAndRole.roleType, roleAndResourcePair.identityAndRole.identityDto, collectionId
    );

    // when
    final Response response = createResourceInCollectionAsKermit(roleAndResourcePair, collectionId);

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_IDENTITY_ROLES_AND_RESOURCE_SCENARIOS)
  public void viewerIdentityIsRejectedToAddResourceByCollectionRole(final IdentityRoleAndResource roleAndResourcePair) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      roleAndResourcePair.identityAndRole.roleType, roleAndResourcePair.identityAndRole.identityDto, collectionId
    );

    // when
    final Response response = createResourceInCollectionAsKermit(roleAndResourcePair, collectionId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = EDIT_USER_ROLES_AND_RESOURCE_SCENARIOS)
  public void editorUserIsGrantedToAddResourceByCollectionRoleAlthoughMemberOfViewerGroupRole(
    final IdentityRoleAndResource roleAndResourcePair) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addKermitGroupRoleToCollectionAsDefaultUser(RoleType.VIEWER, collectionId);
    addRoleToCollectionAsDefaultUser(
      roleAndResourcePair.identityAndRole.roleType, roleAndResourcePair.identityAndRole.identityDto, collectionId
    );

    // when
    final Response response = createResourceInCollectionAsKermit(roleAndResourcePair, collectionId);

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_USER_ROLES_AND_RESOURCE_SCENARIOS)
  public void viewerUserIsRejectedToAddResourceByCollectionRoleAlthoughMemberOfEditorGroup(
    final IdentityRoleAndResource roleAndResourcePair) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addKermitGroupRoleToCollectionAsDefaultUser(RoleType.EDITOR, collectionId);
    addRoleToCollectionAsDefaultUser(
      roleAndResourcePair.identityAndRole.roleType, roleAndResourcePair.identityAndRole.identityDto, collectionId
    );

    // when
    final Response response = createResourceInCollectionAsKermit(roleAndResourcePair, collectionId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void noRoleIdentityIsRejectedToAddResourceToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    createResourceScenariosForRoleType(null)
      .forEach(roleAndResourcePair -> {
        final Response response = createResourceInCollectionAsKermit(roleAndResourcePair, collectionId);

        // then
        assertThat(response.getStatus(), is(403));
      });
  }

  @Test
  @Parameters(method = EDIT_IDENITY_ROLES_AND_RESOURCE_SCENARIOS)
  public void editorIdentityIsGrantedCopyResourceByCollectionRole(final IdentityRoleAndResource roleAndResourcePair) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      roleAndResourcePair.identityAndRole.roleType, roleAndResourcePair.identityAndRole.identityDto, collectionId
    );

    // when
    final String resourceId = createPrivateResourceAsKermit(roleAndResourcePair).getId();
    final Response response = copyResourceToCollectionAsKermit(
      roleAndResourcePair.entityType,
      resourceId,
      collectionId
    );

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_IDENTITY_ROLES_AND_RESOURCE_SCENARIOS)
  public void viewerIdentityIsRejectedToCopyResourceByCollectionRole(final IdentityRoleAndResource roleAndResourcePair) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      roleAndResourcePair.identityAndRole.roleType, roleAndResourcePair.identityAndRole.identityDto, collectionId
    );

    // when
    final String resourceId = createPrivateResourceAsKermit(roleAndResourcePair).getId();
    final Response response = copyResourceToCollectionAsKermit(
      roleAndResourcePair.entityType, resourceId, collectionId
    );

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void noRoleIdentityIsRejectedToCopyResourceToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    createResourceScenariosForRoleType(null)
      .forEach(roleAndResourcePair -> {
        final String resourceId = createPrivateResourceAsKermit(roleAndResourcePair).getId();
        final Response response = copyResourceToCollectionAsKermit(
          roleAndResourcePair.entityType, resourceId, collectionId
        );

        // then
        assertThat(response.getStatus(), is(403));
      });
  }

  private IdDto createPrivateResourceAsKermit(final IdentityRoleAndResource roleAndResourcePair) {
    switch (roleAndResourcePair.entityType) {
      case REPORT:
        switch (roleAndResourcePair.reportType) {
          case PROCESS:
            if (roleAndResourcePair.combined) {
              return getOptimizeRequestExecutorWithKermitAuthentication()
                .buildCreateCombinedReportRequest()
                .execute(IdDto.class, 200);
            } else {
              return getOptimizeRequestExecutorWithKermitAuthentication()
                .buildCreateSingleProcessReportRequest()
                .execute(IdDto.class, 200);
            }
          case DECISION:
            return getOptimizeRequestExecutorWithKermitAuthentication()
              .buildCreateSingleDecisionReportRequest()
              .execute(IdDto.class, 200);
          default:
            throw new OptimizeIntegrationTestException("Unsupported reportType: " + roleAndResourcePair.reportType);
        }
      case DASHBOARD:
        return getOptimizeRequestExecutorWithKermitAuthentication()
          .buildCreateDashboardRequest()
          .execute(IdDto.class, 200);
      default:
        throw new IllegalStateException("Unsupported entityType: " + roleAndResourcePair.entityType);
    }

  }

  private Response copyResourceToCollectionAsKermit(final EntityType entityType,
                                                    final String entityId,
                                                    final String collectionId) {
    switch (entityType) {
      case REPORT:
        return getOptimizeRequestExecutorWithKermitAuthentication()
          .buildCopyReportRequest(entityId, collectionId)
          .execute();
      case DASHBOARD:
        return getOptimizeRequestExecutorWithKermitAuthentication()
          .buildCopyDashboardRequest(entityId, collectionId)
          .execute();
      default:
        throw new OptimizeIntegrationTestException("Unsupported entityType: " + entityType);
    }
  }

  private Response createResourceInCollectionAsKermit(final IdentityRoleAndResource roleAndResourcePair,
                                                      final String collectionId) {
    switch (roleAndResourcePair.entityType) {
      case REPORT:
        switch (roleAndResourcePair.reportType) {
          case PROCESS:
            if (roleAndResourcePair.combined) {
              return getOptimizeRequestExecutorWithKermitAuthentication()
                .buildCreateCombinedReportRequest(collectionId)
                .execute();
            } else {
              return getOptimizeRequestExecutorWithKermitAuthentication()
                .buildCreateSingleProcessReportRequest(collectionId)
                .execute();
            }
          case DECISION:
            return getOptimizeRequestExecutorWithKermitAuthentication()
              .buildCreateSingleDecisionReportRequest(collectionId)
              .execute();
          default:
            throw new OptimizeIntegrationTestException("Unsupported reportType: " + roleAndResourcePair.reportType);
        }
      case DASHBOARD:
        return getOptimizeRequestExecutorWithKermitAuthentication()
          .buildCreateDashboardRequest(collectionId)
          .execute();
      default:
        throw new OptimizeIntegrationTestException("Unsupported entityType: " + roleAndResourcePair.entityType);
    }
  }

}
