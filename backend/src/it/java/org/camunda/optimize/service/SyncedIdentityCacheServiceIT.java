/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import org.camunda.optimize.service.util.configuration.engine.IdentitySyncConfiguration;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.importing.EngineConstants.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.KERMIT_GROUP_NAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class SyncedIdentityCacheServiceIT extends AbstractIT {

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private static final String USER_KERMIT = "kermit";
  private static final String TEST_GROUP = "testGroup";
  private static final String TEST_GROUP_B = "anotherTestGroup";

  @Test
  public void verifySyncEnabledByDefault() {
    assertThat(getSyncedIdentityCacheService().isScheduledToRun()).isTrue();
  }

  @Test
  public void testSyncStoppedSuccessfully() {
    try {
      getSyncedIdentityCacheService().stopSchedulingUserSync();
      assertThat(getSyncedIdentityCacheService().isScheduledToRun()).isFalse();
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

      // then
      assertThat(getSyncedIdentityCacheService().getUserIdentityById(userIdJohn)).isPresent();
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

      // then
      final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
      assertThatThrownBy(syncedIdentityCacheService::synchronizeIdentities)
        .isInstanceOf(MaxEntryLimitHitException.class);
      assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
      assertThat(getSyncedIdentityCacheService().getUserIdentityById(userIdJohn)).isNotPresent();
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
    assertThat(userIdentityById).isPresent();
    assertThat(userIdentityById.get().getName()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(userIdentityById.get().getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
    assertThat(userIdentityById.get().getLastName()).isEqualTo(DEFAULT_LASTNAME);
    assertThat(userIdentityById.get().getEmail()).contains(DEFAULT_EMAIL_DOMAIN);
  }

  @Test
  public void testGrantedUserIsImportedMetaNotAvailable() {
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    getIdentitySyncConfiguration().setIncludeUserMetaData(false);

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    final Optional<UserDto> userIdentityById = getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER);
    assertThat(userIdentityById).isPresent();
    assertThat(userIdentityById.get().getName()).isEqualTo(KERMIT_USER);
    assertThat(userIdentityById.get().getFirstName()).isNull();
    assertThat(userIdentityById.get().getLastName()).isNull();
    assertThat(userIdentityById.get().getEmail()).isNull();
  }

  @Test
  public void testNotGrantedUserIsNotImported() {
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
  }

  @Test
  public void testGrantedGroupIsImported() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    final Optional<GroupDto> groupIdentityById = getSyncedIdentityCacheService().getGroupIdentityById(GROUP_ID);
    assertThat(groupIdentityById).isPresent();
    assertThat(groupIdentityById.get().getName()).isEqualTo(KERMIT_GROUP_NAME);
    assertThat(groupIdentityById.get().getMemberCount()).isEqualTo(1L);
  }

  @Test
  public void testNotGrantedGroupIsNotImported() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getGroupIdentityById(GROUP_ID)).isNotPresent();
  }

  @Test
  public void testGrantedGroupMemberIsImported() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
  }

  @Test
  public void testNotGrantedGroupMemberIsNotImported() {
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
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

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
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

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
  }

  @Test
  public void testGlobalAuthNoExplicitGrantUserIsImported() {
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
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

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
  }

  @Test
  public void testGlobalAuthNoExplicitGrantGroupIsImported() {
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getGroupIdentityById(GROUP_ID)).isPresent();
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

    assertThat(getSyncedIdentityCacheService().getGroupIdentityById(GROUP_ID)).isNotPresent();
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

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
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

      CollectionRoleRequestDto testGroupRole = new CollectionRoleRequestDto(
        new IdentityDto(TEST_GROUP, IdentityType.GROUP),
        RoleType.EDITOR
      );
      CollectionRoleRequestDto testGroupBRole = new CollectionRoleRequestDto(
        new IdentityDto(TEST_GROUP_B, IdentityType.GROUP),
        RoleType.EDITOR
      );
      CollectionRoleRequestDto userKermitRole = new CollectionRoleRequestDto(
        new IdentityDto(USER_KERMIT, IdentityType.USER),
        RoleType.EDITOR
      );
      CollectionRoleRequestDto userDemoRole = new CollectionRoleRequestDto(
        new IdentityDto(DEFAULT_USERNAME, IdentityType.USER),
        RoleType.MANAGER
      );

      collectionClient.addRolesToCollection(collectionId1, testGroupRole);
      collectionClient.addRolesToCollection(collectionId1, testGroupBRole);
      collectionClient.addRolesToCollection(collectionId1, userKermitRole);

      collectionClient.addRolesToCollection(collectionId2, testGroupRole);
      collectionClient.addRolesToCollection(collectionId2, testGroupBRole);
      collectionClient.addRolesToCollection(collectionId2, userKermitRole);

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
      List<IdResponseDto> roleIds1 = collectionClient.getCollectionRoleIdDtos(collectionId1);
      List<IdResponseDto> roleIds2 = collectionClient.getCollectionRoleIdDtos(collectionId2);
      assertThat(roleIds1).containsExactlyInAnyOrderElementsOf(roleIds2);
      assertThat(roleIds1).containsExactlyInAnyOrder(
        new IdResponseDto(testGroupBRole.getId()),
        new IdResponseDto(userDemoRole.getId())
      );
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
      List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);
      assertThat(roles).isEmpty();
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
