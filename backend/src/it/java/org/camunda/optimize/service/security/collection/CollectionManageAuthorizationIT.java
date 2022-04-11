/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class CollectionManageAuthorizationIT extends AbstractCollectionRoleIT {

  private static final String USER_ID_JOHN = "John";

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateNameByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    addRoleToCollectionAsDefaultUser(
      identityAndRole.roleType,
      identityAndRole.identityDto,
      collectionId
    );

    // when
    final PartialCollectionDefinitionRequestDto collectionRenameDto = new PartialCollectionDefinitionRequestDto("Test");

    // then
    collectionClient.updateCollection(collectionId, collectionRenameDto);
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityIsRejectedToUpdateNameByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    final PartialCollectionDefinitionRequestDto collectionRenameDto = new PartialCollectionDefinitionRequestDto("Test");
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityCanUpdateNameByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    // when
    final PartialCollectionDefinitionRequestDto collectionRenameDto = new PartialCollectionDefinitionRequestDto("Test");

    // then
    collectionClient.updateCollection(collectionId, collectionRenameDto);
  }

  @Test
  public void superGroupIdentityCanUpdateNameByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    // when
    final PartialCollectionDefinitionRequestDto collectionRenameDto = new PartialCollectionDefinitionRequestDto("Test");

    // then
    collectionClient.updateCollection(collectionId, collectionRenameDto);
  }

  @Test
  public void noRoleUserIsRejectedToUpdateName() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String collectionId = collectionClient.createNewCollection();

    // when
    final PartialCollectionDefinitionRequestDto collectionRenameDto = new PartialCollectionDefinitionRequestDto("Test");
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanCreateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_USER);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    final CollectionRoleRequestDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    collectionClient.addRoleToCollectionAsUser(collectionId, collectionRoleDto, KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToCreateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    final CollectionRoleRequestDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddRolesToCollectionRequest(collectionId, collectionRoleDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityCanCreateRoleByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_USER);
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final CollectionRoleRequestDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    collectionClient.addRoleToCollectionAsUser(collectionId, collectionRoleDto, KERMIT_USER, KERMIT_USER);
  }

  @Test
  public void superGroupIdentityCanCreateRoleByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_USER);
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final CollectionRoleRequestDto collectionRoleDto = createJohnEditorRoleDto();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);

    // when
    collectionClient.addRoleToCollectionAsUser(collectionId, collectionRoleDto, KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    collectionClient.addRolesToCollection(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_USER);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when + then
    collectionClient.updateCollectionRoleAsUser(
      collectionId,
      getJohnRoleId(),
      new CollectionRoleUpdateRequestDto(RoleType.MANAGER),
      KERMIT_USER,
      KERMIT_USER
    );
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToUpdateRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    final CollectionRoleRequestDto johnEditorRoleDto = createJohnEditorRoleDto();
    collectionClient.addRolesToCollection(collectionId, johnEditorRoleDto);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_USER);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateRoleToCollectionRequest(collectionId, getJohnRoleId(), new CollectionRoleUpdateRequestDto(RoleType.MANAGER))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityCanUpdateRoleByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    collectionClient.addRolesToCollection(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_USER);
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    // when
    collectionClient.updateCollectionRoleAsUser(
      collectionId,
      getJohnRoleId(),
      new CollectionRoleUpdateRequestDto(RoleType.MANAGER),
      KERMIT_USER,
      KERMIT_USER
    );
  }

  @Test
  public void superGroupIdentityCanUpdateRoleByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    collectionClient.addRolesToCollection(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_USER);
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    // when
    collectionClient.updateCollectionRoleAsUser(
      collectionId,
      getJohnRoleId(),
      new CollectionRoleUpdateRequestDto(RoleType.MANAGER),
      KERMIT_USER,
      KERMIT_USER
    );
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanDeleteRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    collectionClient.addRolesToCollection(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_USER);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    collectionClient.deleteCollectionRoleAsUser(collectionId, getJohnRoleId(), KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToDeleteRoleByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    collectionClient.addRolesToCollection(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteRoleToCollectionRequest(collectionId, getJohnRoleId())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityCanDeleteRoleByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    collectionClient.addRolesToCollection(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_USER);
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    // when
    collectionClient.deleteCollectionRoleAsUser(collectionId, getJohnRoleId(), KERMIT_USER, KERMIT_USER);
  }

  @Test
  public void superGroupIdentityCanDeleteRoleByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addUserAndGrantOptimizeAccess(USER_ID_JOHN);
    collectionClient.addRolesToCollection(collectionId, createJohnEditorRoleDto());

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_USER);
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    // when
    collectionClient.deleteCollectionRoleAsUser(collectionId, getJohnRoleId(), KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanCreateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    collectionClient.addScopeEntryToCollectionAsUser(collectionId, createProcessScope(), KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToCreateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildAddScopeEntryToCollectionRequest(collectionId, createProcessScope())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityCanCreateScopeByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    // when
    collectionClient.addScopeEntryToCollectionAsUser(collectionId, createProcessScope(), KERMIT_USER, KERMIT_USER);
  }

  @Test
  public void superGroupIdentityCanCreateScopeByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantAllResourceAuthorizationsForKermitGroup(RESOURCE_TYPE_PROCESS_DEFINITION);
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    // when
    collectionClient.addScopeEntryToCollectionAsUser(collectionId, createProcessScope(), KERMIT_USER, KERMIT_USER);
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanUpdateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    collectionClient.updateCollectionScopeAsKermit(collectionId, scopeEntry.getId(), Collections.singletonList("tenant1"));
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToUpdateScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntry.getId(), createScopeUpdate())
      .execute();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityCanUpdateScopeByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    // when
    collectionClient.updateCollectionScopeAsKermit(
      collectionId,
      scopeEntry.getId(),
      Collections.singletonList("tenant1")
    );
  }

  @Test
  public void superGroupIdentityCanUpdateScopeByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    // when
    collectionClient.updateCollectionScopeAsKermit(
      collectionId,
      scopeEntry.getId(),
      Collections.singletonList("tenant1")
    );
  }

  @ParameterizedTest
  @MethodSource(MANAGER_IDENTITY_ROLES)
  public void managerIdentityCanDeleteScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource(NON_MANAGER_IDENTITY_ROLES)
  public void nonManagerIdentityRejectedToDeleteScopeByCollectionRole(final IdentityAndRole identityAndRole) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    addRoleToCollectionAsDefaultUser(identityAndRole.roleType, identityAndRole.identityDto, collectionId);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void superUserIdentityCanDeleteScopeByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void superGroupIdentityCanDeleteScopeByCollectionRole() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry = createProcessScope();
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    // when
    Response response = getOptimizeRequestExecutorWithKermitAuthentication()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void onlyManagerCanCopyACollection() {
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    collectionClient.addRolesToCollection(collectionId, new CollectionRoleRequestDto(
        new IdentityDto("kermit", IdentityType.USER),
        RoleType.VIEWER
      ));

    authorizationClient.addUserAndGrantOptimizeAccess("gonzo");
    collectionClient.addRolesToCollection(collectionId, new CollectionRoleRequestDto(
        new IdentityDto("gonzo", IdentityType.USER),
        RoleType.MANAGER
      ));

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCopyCollectionRequest(collectionId)
      .withUserAuthentication("gonzo", "gonzo")
      .execute(Response.Status.OK.getStatusCode());

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCopyCollectionRequest(collectionId)
      .withUserAuthentication("kermit", "kermit")
      .execute(Response.Status.FORBIDDEN.getStatusCode());
  }

  private CollectionScopeEntryUpdateDto createScopeUpdate() {
    return new CollectionScopeEntryUpdateDto(Collections.singletonList("tenant1"));
  }

  private CollectionScopeEntryDto createProcessScope() {
    return new CollectionScopeEntryDto(DefinitionType.PROCESS, "KEY");
  }

  private CollectionRoleRequestDto createJohnEditorRoleDto() {
    return new CollectionRoleRequestDto(new IdentityDto(USER_ID_JOHN, IdentityType.USER), RoleType.EDITOR);
  }

  private String getJohnRoleId() {
    return createJohnEditorRoleDto().getId();
  }

}
