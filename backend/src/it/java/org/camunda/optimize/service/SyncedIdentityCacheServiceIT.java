/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.service.util.configuration.engine.IdentitySyncConfiguration;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.KERMIT_GROUP_NAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SyncedIdentityCacheServiceIT extends AbstractIT {

  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(
    engineIntegrationExtension.getEngineName()
  );

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private static final String USER_KERMIT = "kermit";
  private static final String TEST_GROUP = "testGroup";
  private static final String TEST_GROUP_B = "anotherTestGroup";

  @Test
  public void verifySyncEnabledByDefault() {
    assertThat(getSyncedIdentityCacheService().isScheduledToRun(), is(true));
  }

  @Test
  public void testSyncStoppedSuccessfully() {
    try {
      getSyncedIdentityCacheService().stopSchedulingUserSync();
      assertThat(getSyncedIdentityCacheService().isScheduledToRun(), is(false));
    } finally {
      getSyncedIdentityCacheService().startSchedulingUserSync();
    }
  }

  @Test
  public void testCacheReplacedOnNewSync() {
    try {
      // given
      getSyncedIdentityCacheService().stopSchedulingUserSync();
      authorizationClient.addKermitUserAndGrantAccessToOptimize();
      getSyncedIdentityCacheService().synchronizeIdentities();

      // when
      final String userIdJohn = "john";
      authorizationClient.addUserAndGrantOptimizeAccess(userIdJohn);
      getSyncedIdentityCacheService().synchronizeIdentities();

      //then
      assertThat(getSyncedIdentityCacheService().getUserIdentityById(userIdJohn).isPresent(), is(true));
    } finally {
      getSyncedIdentityCacheService().startSchedulingUserSync();
    }
  }

  @Test
  public void testCacheNotReplacedOnLimitHit() {
    try {
      // given
      getSyncedIdentityCacheService().stopSchedulingUserSync();
      authorizationClient.addKermitUserAndGrantAccessToOptimize();
      getSyncedIdentityCacheService().synchronizeIdentities();

      // when
      final String userIdJohn = "john";
      authorizationClient.addUserAndGrantOptimizeAccess(userIdJohn);
      // we have at least two users, but limit is now 1
      getIdentitySyncConfiguration().setMaxEntryLimit(1L);
      getSyncedIdentityCacheService().synchronizeIdentities();

      // then
      assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(true));
      assertThat(getSyncedIdentityCacheService().getUserIdentityById(userIdJohn).isPresent(), is(false));
    } finally {
      getSyncedIdentityCacheService().startSchedulingUserSync();
    }
  }

  @Test
  public void testGrantedUserIsImportedMetaDataAvailable() {
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    final Optional<UserDto> userIdentityById = getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER);
    assertThat(userIdentityById.isPresent(), is(true));
    assertThat(userIdentityById.get().getName(), is(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME));
    assertThat(userIdentityById.get().getFirstName(), is(DEFAULT_FIRSTNAME));
    assertThat(userIdentityById.get().getLastName(), is(DEFAULT_LASTNAME));
    assertThat(userIdentityById.get().getEmail(), containsString(DEFAULT_EMAIL_DOMAIN));
  }

  @Test
  public void testGrantedUserIsImportedMetaNotAvailable() {
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    getIdentitySyncConfiguration().setIncludeUserMetaData(false);

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    final Optional<UserDto> userIdentityById = getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER);
    assertThat(userIdentityById.isPresent(), is(true));
    assertThat(userIdentityById.get().getName(), is(nullValue()));
    assertThat(userIdentityById.get().getFirstName(), is(nullValue()));
    assertThat(userIdentityById.get().getLastName(), is(nullValue()));
    assertThat(userIdentityById.get().getEmail(), is(nullValue()));
  }

  @Test
  public void testNotGrantedUserIsNotImported() {
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(false));
  }

  @Test
  public void testGrantedGroupIsImported() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    final Optional<GroupDto> groupIdentityById = getSyncedIdentityCacheService().getGroupIdentityById(GROUP_ID);
    assertThat(groupIdentityById.isPresent(), is(true));
    assertThat(groupIdentityById.get().getName(), is(KERMIT_GROUP_NAME));
    assertThat(groupIdentityById.get().getMemberCount(), is(1L));
  }

  @Test
  public void testNotGrantedGroupIsNotImported() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getGroupIdentityById(GROUP_ID).isPresent(), is(false));
  }

  @Test
  public void testGrantedGroupMemberIsImported() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(true));
  }

  @Test
  public void testNotGrantedGroupMemberIsNotImported() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(false));
  }

  @Test
  public void testGrantedGroupMemberIsImportedAlthoughAlsoMemberOfNotGrantedGroup() {
    // https://docs.camunda.org/manual/7.11/user-guide/process-engine/authorization-service/#authorization-precedence
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    final String revokedGroupId = "revokedGroup";
    authorizationClient.createGroupAndAddUser(revokedGroupId, KERMIT_USER);
    authorizationClient.revokeSingleResourceAuthorizationsForGroup(
      revokedGroupId, OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(true));
  }

  @Test
  public void testRevokedUserIsNotImportedAlthoughMemberOfGrantedGroup() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(false));
  }

  @Test
  public void testGlobalAuthNoExplicitGrantUserIsImported() {
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(true));
  }

  @Test
  public void testGlobalAuthRevokedAuthUserIsNotImported() {
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(false));
  }

  @Test
  public void testGlobalAuthNoExplicitGrantGroupIsImported() {
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getGroupIdentityById(GROUP_ID).isPresent(), is(true));
  }

  @Test
  public void testGlobalAuthRevokedAuthGroupIsNotImported() {
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getGroupIdentityById(GROUP_ID).isPresent(), is(false));
  }

  @Test
  public void testGlobalAuthRevokedAuthGroupMemberIsNotImported() {
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(false));
  }

  @Test
  public void testPermissionCleanupAfterIdentitySync() {
    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    try {
      // given a collection with permissions for users/groups
      syncedIdentityCacheService.stopSchedulingUserSync();

      authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
      authorizationClient.addKermitUserWithoutAuthorizations();
      authorizationClient.addUserAndGrantOptimizeAccess(DEFAULT_USERNAME);
      authorizationClient.createGroupAndAddUser(TEST_GROUP, USER_KERMIT);
      authorizationClient.createGroupAndAddUser(TEST_GROUP_B, DEFAULT_USERNAME);

      syncedIdentityCacheService.synchronizeIdentities();

      final String collectionId1 = collectionClient.createNewCollection();
      final String collectionId2 = collectionClient.createNewCollection();

      CollectionRoleDto testGroupRole = new CollectionRoleDto(new GroupDto(TEST_GROUP), RoleType.EDITOR);
      CollectionRoleDto testGroupBRole = new CollectionRoleDto(new GroupDto(TEST_GROUP_B), RoleType.EDITOR);
      CollectionRoleDto userKermitRole = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
      CollectionRoleDto userDemoRole = new CollectionRoleDto(new UserDto(DEFAULT_USERNAME), RoleType.MANAGER);

      collectionClient.addRoleToCollection(collectionId1, testGroupRole);
      collectionClient.addRoleToCollection(collectionId1, testGroupBRole);
      collectionClient.addRoleToCollection(collectionId1, userKermitRole);

      collectionClient.addRoleToCollection(collectionId2, testGroupRole);
      collectionClient.addRoleToCollection(collectionId2, testGroupBRole);
      collectionClient.addRoleToCollection(collectionId2, userKermitRole);

      // when users/groups are removed from identityCache
      authorizationClient.revokeSingleResourceAuthorizationsForGroup(
        TEST_GROUP,
        OPTIMIZE_APPLICATION_RESOURCE_ID,
        RESOURCE_TYPE_APPLICATION
      );
      authorizationClient.revokeSingleResourceAuthorizationsForUser(
        USER_KERMIT,
        OPTIMIZE_APPLICATION_RESOURCE_ID,
        RESOURCE_TYPE_APPLICATION
      );

      syncedIdentityCacheService.synchronizeIdentities();

      // then users/groups no longer existing in identityCache have been removed from the collection's permissions
      List<CollectionRoleDto> roles1 = collectionClient.getAllRolesForCollection(collectionId1);
      List<CollectionRoleDto> roles2 = collectionClient.getAllRolesForCollection(collectionId2);
      assertThat(roles1.containsAll(roles2) && roles1.size() == roles2.size(), is(true));
      assertThat(roles1).containsExactlyInAnyOrder(testGroupBRole, userDemoRole);
    } finally {
      syncedIdentityCacheService.startSchedulingUserSync();
    }
  }

  @Test
  public void testPermissionCleanupAfterIdentitySyncRemovesLastManager() {
    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    try {
      // given
      syncedIdentityCacheService.stopSchedulingUserSync();

      authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
      authorizationClient.addKermitUserWithoutAuthorizations();
      embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(DEFAULT_USERNAME);

      syncedIdentityCacheService.synchronizeIdentities();

      final String collectionId = collectionClient.createNewCollection(USER_KERMIT, USER_KERMIT);

      // when
      authorizationClient.revokeSingleResourceAuthorizationsForUser(
        USER_KERMIT,
        OPTIMIZE_APPLICATION_RESOURCE_ID,
        RESOURCE_TYPE_APPLICATION
      );

      syncedIdentityCacheService.synchronizeIdentities();

      // then
      List<CollectionRoleDto> roles = collectionClient.getAllRolesForCollection(collectionId);
      assertThat(roles.isEmpty(), is(true));
    } finally {
      syncedIdentityCacheService.startSchedulingUserSync();
    }
  }

  private SyncedIdentityCacheService getSyncedIdentityCacheService() {
    return embeddedOptimizeExtension.getSyncedIdentityCacheService();
  }

  private IdentitySyncConfiguration getIdentitySyncConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getIdentitySyncConfiguration();
  }
}
