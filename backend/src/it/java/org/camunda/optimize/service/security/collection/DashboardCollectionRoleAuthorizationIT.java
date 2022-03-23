/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class DashboardCollectionRoleAuthorizationIT extends AbstractCollectionRoleIT {

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedAddDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToAddDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(EDIT_USER_ROLES)
  public void editorUserIsGrantedToAddDashboardByCollectionRoleAlthoughMemberOfViewerGroupRole(
    final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addKermitGroupRoleToCollectionAsDefaultUser(RoleType.VIEWER, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void superUserIdentityIsGrantedAddDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void superGroupIdentityIsGrantedAddDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_USER_ROLES)
  public void viewerUserIsRejectedToAddDashboardByCollectionRoleAlthoughMemberOfEditorGroup(
    final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addKermitGroupRoleToCollectionAsDefaultUser(RoleType.EDITOR, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void noRoleIdentityIsRejectedToAddDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedCopyCollectionDashboardInsideCollectionByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String resourceId = dashboardClient.createEmptyDashboard(collectionId);
    final Response response = copyDashboardAsKermit(resourceId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final DashboardDefinitionRestDto dashboardCopy = dashboardClient.getDashboard(copyId);
    assertThat(dashboardCopy.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToCopyCollectionDashboardInsideCollectionByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String resourceId = dashboardClient.createEmptyDashboard(collectionId);
    final Response response = copyDashboardAsKermit(resourceId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedCopyPrivateDashboardToCollectionByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String resourceId = dashboardClient.createDashboardAsUser(null, KERMIT_USER, KERMIT_USER);
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final DashboardDefinitionRestDto dashboardCopy = dashboardClient.getDashboard(copyId);
    assertThat(dashboardCopy.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToCopyPrivateDashboardToCollectionByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String resourceId = dashboardClient.createDashboardAsUser(null, KERMIT_USER, KERMIT_USER);
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void noRoleIdentityIsRejectedToCopyPrivateDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final String resourceId = dashboardClient.createDashboardAsUser(null, KERMIT_USER, KERMIT_USER);
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityIsGrantedCopyOtherPrivateDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final String resourceId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final DashboardDefinitionRestDto dashboardCopy = dashboardClient.getDashboard(copyId);
    assertThat(dashboardCopy.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @Test
  public void superGroupIdentityIsGrantedCopyOtherPrivateDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    final String resourceId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final DashboardDefinitionRestDto dashboardCopy = dashboardClient.getDashboard(copyId);
    assertThat(dashboardCopy.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES)
  public void anyRoleIdentityIsGrantedCopyCollectionDashboardAsPrivateDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = copyDashboardAsPrivateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String copyId = response.readEntity(IdResponseDto.class).getId();
    final DashboardDefinitionRestDto dashboardCopy = getDashboardByIdAsKermit(copyId).getDefinitionDto();
    assertThat(dashboardCopy.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @Test
  public void superUserIdentityIsGrantedCopyCollectionDashboardAsPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = copyDashboardAsPrivateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void superGroupIdentityIsGrantedCopyCollectionDashboardAsPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = copyDashboardAsPrivateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void noRoleIdentityIsRejectedToCopyCollectionDashboardAsPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = copyDashboardAsPrivateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void accessOtherPrivateDashboardFails() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDashboardRequest(dashboardId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserCanAccessOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    dashboardClient.getDashboard(dashboardId);
  }

  @Test
  public void superGroupCanAccessOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    dashboardClient.getDashboard(dashboardId);
  }

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES)
  public void anyRoleIdentityIsGrantedAccessToCollectionDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final AuthorizedDashboardDefinitionResponseDto dashboard = getDashboardByIdAsKermit(dashboardId);

    // then
    assertThat(dashboard.getCurrentUserRole()).isEqualTo(getExpectedResourceRoleForCollectionRole(identityAndRole));
  }

  @Test
  public void superUserIdentityIsGrantedAccessToCollectionDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final AuthorizedDashboardDefinitionResponseDto dashboard = getDashboardByIdAsKermit(dashboardId);

    // then
    assertThat(dashboard.getCurrentUserRole()).isEqualTo(RoleType.EDITOR);
  }

  @Test
  public void superGroupIdentityIsGrantedAccessToCollectionDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final AuthorizedDashboardDefinitionResponseDto dashboard = getDashboardByIdAsKermit(dashboardId);

    // then
    assertThat(dashboard.getCurrentUserRole()).isEqualTo(RoleType.EDITOR);
  }

  @Test
  public void noRoleIdentityIsRejectedAccessToCollectionDashboardAsPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDashboardRequest(dashboardId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateOtherPrivateDashboardFails() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserCanUpdateOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void superGroupCanUpdateOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedUpdateDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToUpdateDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityIsGrantedUpdateCollectionDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void superGroupIdentityIsGrantedUpdateCollectionDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void noRoleIdentityIsRejectedToUpdateDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void deleteOtherPrivateDashboardFails() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserCanDeleteOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void superGroupCanDeleteOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String dashboardId = dashboardClient.createDashboardAsUser(null, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedDeleteDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToDeleteDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityIsGrantedDeleteDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void superGroupIdentityIsGrantedDeleteDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void noRoleIdentityIsRejectedToDeleteDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private Response createDashboardInCollectionAsKermit(final String collectionId) {
    return dashboardClient.createDashboardAsUserGetRawResponse(
      collectionId,
      new ArrayList<>(),
      KERMIT_USER,
      KERMIT_USER
    );
  }

  private Response copyDashboardAsPrivateDashboardAsKermit(final String dashboardId) {
    // explicit null string triggers copy to private
    return copyDashboardToCollectionAsKermit(dashboardId, "null");
  }

  private Response copyDashboardAsKermit(final String dashboardId) {
    return copyDashboardToCollectionAsKermit(dashboardId, null);
  }

  private Response copyDashboardToCollectionAsKermit(final String dashboardId, final String collectionId) {
    return dashboardClient.copyDashboardToCollectionAsUserAndGetRawResponse(
      dashboardId,
      collectionId,
      KERMIT_USER,
      KERMIT_USER
    );
  }

  private AuthorizedDashboardDefinitionResponseDto getDashboardByIdAsKermit(final String dashboardId) {
    return dashboardClient.getDashboardAsUser(dashboardId, KERMIT_USER, KERMIT_USER);
  }

  private Response updateDashboardAsKermit(final String dashboardId) {
    return dashboardClient.updateDashboardAsUser(
      dashboardId,
      new DashboardDefinitionRestDto(),
      KERMIT_USER,
      KERMIT_USER
    );
  }

  private Response deleteDashboardAsKermit(final String dashboardId) {
    return dashboardClient.deleteDashboardAsUser(dashboardId, KERMIT_USER, KERMIT_USER, false);
  }

}
