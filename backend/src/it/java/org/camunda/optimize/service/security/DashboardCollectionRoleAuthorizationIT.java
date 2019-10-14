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
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionDto;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class DashboardCollectionRoleAuthorizationIT extends AbstractCollectionRoleIT {
  @Test
  @Parameters(method = EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedAddDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToAddDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = EDIT_USER_ROLES)
  public void editorUserIsGrantedToAddDashboardByCollectionRoleAlthoughMemberOfViewerGroupRole(
    final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addKermitGroupRoleToCollectionAsDefaultUser(RoleType.VIEWER, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void superUserIdentityIsGrantedAddDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_USER_ROLES)
  public void viewerUserIsRejectedToAddDashboardByCollectionRoleAlthoughMemberOfEditorGroup(
    final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addKermitGroupRoleToCollectionAsDefaultUser(RoleType.EDITOR, collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void noRoleIdentityIsRejectedToAddDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    final Response response = createDashboardInCollectionAsKermit(collectionId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedCopyCollectionDashboardInsideCollectionByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String resourceId = createDashboardInCollectionAsDefaultUser(collectionId);
    final Response response = copyDashboardAsKermit(resourceId);

    // then
    assertThat(response.getStatus(), is(200));
    final String copyId = response.readEntity(IdDto.class).getId();
    final DashboardDefinitionDto dashboardCopy = getDashboardByIdAsDefaultUser(copyId);
    assertThat(dashboardCopy.getOwner(), is(KERMIT_USER));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToCopyCollectionDashboardInsideCollectionByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String resourceId = createDashboardInCollectionAsDefaultUser(collectionId);
    final Response response = copyDashboardAsKermit(resourceId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedCopyPrivateDashboardToCollectionByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String resourceId = createPrivateDashboardAsKermit();
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus(), is(200));
    final String copyId = response.readEntity(IdDto.class).getId();
    final DashboardDefinitionDto dashboardCopy = getDashboardByIdAsDefaultUser(copyId);
    assertThat(dashboardCopy.getOwner(), is(KERMIT_USER));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToCopyPrivateDashboardToCollectionByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final String resourceId = createPrivateDashboardAsKermit();
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void noRoleIdentityIsRejectedToCopyPrivateDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    final String resourceId = createPrivateDashboardAsKermit();
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityIsGrantedCopyOtherPrivateDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = createNewCollectionAsDefaultUser();

    // when
    final String resourceId = createPrivateDashboardAsDefaultUser();
    final Response response = copyDashboardToCollectionAsKermit(resourceId, collectionId);

    // then
    assertThat(response.getStatus(), is(200));
    final String copyId = response.readEntity(IdDto.class).getId();
    final DashboardDefinitionDto dashboardCopy = getDashboardByIdAsDefaultUser(copyId);
    assertThat(dashboardCopy.getOwner(), is(KERMIT_USER));
  }

  @Test
  @Parameters(method = ACCESS_IDENTITY_ROLES)
  public void anyRoleIdentityIsGrantedCopyCollectionDashboardAsPrivateDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = copyDashboardAsPrivateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(200));
    final String copyId = response.readEntity(IdDto.class).getId();
    final DashboardDefinitionDto dashboardCopy = getDashboardByIdAsKermit(copyId).getDefinitionDto();
    assertThat(dashboardCopy.getOwner(), is(KERMIT_USER));
  }

  @Test
  public void superUserIdentityIsGrantedCopyCollectionDashboardAsPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);

    // when
    final Response response = copyDashboardAsPrivateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void noRoleIdentityIsRejectedToCopyCollectionDashboardAsPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);

    // when
    final Response response = copyDashboardAsPrivateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void accessOtherPrivateDashboardFails() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String dashboardId = createPrivateDashboardAsDefaultUser();

    // when
    final Response response = embeddedOptimizeRule.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDashboardRequest(dashboardId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserCanAccessOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String dashboardId = createPrivateDashboardAsDefaultUser();

    // when
    final Response response = embeddedOptimizeRule.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDashboardRequest(dashboardId)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = ACCESS_IDENTITY_ROLES)
  public void anyRoleIdentityIsGrantedAccessToCollectionDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final AuthorizedDashboardDefinitionDto dashboard = getDashboardByIdAsKermit(dashboardId);

    // then
    assertThat(dashboard.getCurrentUserRole(), is(getExpectedResourceRoleForCollectionRole(identityAndRole)));
  }

  @Test
  public void superUserIdentityIsGrantedAccessToCollectionDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);

    // when
    final AuthorizedDashboardDefinitionDto dashboard = getDashboardByIdAsKermit(dashboardId);

    // then
    assertThat(dashboard.getCurrentUserRole(), is(RoleType.EDITOR));
  }

  @Test
  public void noRoleIdentityIsRejectedAccessToCollectionDashboardAsPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);

    // when
    final Response response = embeddedOptimizeRule.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDashboardRequest(dashboardId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void updateOtherPrivateDashboardFails() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String dashboardId = createPrivateDashboardAsDefaultUser();

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserCanUpdateOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String dashboardId = createPrivateDashboardAsDefaultUser();

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedUpdateDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToUpdateDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityIsGrantedUpdateCollectionDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void noRoleIdentityIsRejectedToUpdateDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);

    // when
    final Response response = updateDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void deleteOtherPrivateDashboardFails() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String dashboardId = createPrivateDashboardAsDefaultUser();

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserCanDeleteOtherPrivateDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String dashboardId = createPrivateDashboardAsDefaultUser();

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = EDIT_IDENTITY_ROLES)
  public void editorIdentityIsGrantedDeleteDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = ACCESS_ONLY_IDENTITY_ROLES)
  public void viewerIdentityIsRejectedToDeleteDashboardByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void superUserIdentityIsGrantedDeleteDashboard() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void noRoleIdentityIsRejectedToDeleteDashboardToCollection() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    final String dashboardId = createDashboardInCollectionAsDefaultUser(collectionId);

    // when
    final Response response = deleteDashboardAsKermit(dashboardId);

    // then
    assertThat(response.getStatus(), is(403));
  }

  private String createPrivateDashboardAsDefaultUser() {
    return createPrivateDashboardAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String createPrivateDashboardAsKermit() {
    return createPrivateDashboardAsUser(KERMIT_USER, KERMIT_USER);
  }

  private String createPrivateDashboardAsUser(final String user, final String password) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateDashboardRequest(null)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createDashboardInCollectionAsDefaultUser(final String collectionId) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private Response createDashboardInCollectionAsKermit(final String collectionId) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute();
  }

  private Response copyDashboardAsPrivateDashboardAsKermit(final String dashboardId) {
    // explicit null string triggers copy to private
    return copyDashboardToCollectionAsKermit(dashboardId, "null");
  }

  private Response copyDashboardAsKermit(final String dashboardId) {
    return copyDashboardToCollectionAsKermit(dashboardId, null);
  }

  private Response copyDashboardToCollectionAsKermit(final String dashboardId, final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCopyDashboardRequest(dashboardId, collectionId)
      .execute();
  }

  private DashboardDefinitionDto getDashboardByIdAsDefaultUser(final String dashboardId) {
    return embeddedOptimizeRule.getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, 200);
  }

  private AuthorizedDashboardDefinitionDto getDashboardByIdAsKermit(final String dashboardId) {
    return embeddedOptimizeRule.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetDashboardRequest(dashboardId)
      .execute(AuthorizedDashboardDefinitionDto.class, 200);
  }

  private Response updateDashboardAsKermit(final String dashboardId) {
    return embeddedOptimizeRule.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateDashboardRequest(dashboardId, new DashboardDefinitionDto())
      .execute();
  }

  private Response deleteDashboardAsKermit(final String dashboardId) {
    return embeddedOptimizeRule.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildDeleteDashboardRequest(dashboardId)
      .execute();
  }

}
