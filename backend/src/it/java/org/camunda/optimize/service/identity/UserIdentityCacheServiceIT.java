/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import org.camunda.optimize.service.MaxEntryLimitHitException;
import org.camunda.optimize.service.util.configuration.engine.UserIdentityCacheConfiguration;
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

public class UserIdentityCacheServiceIT extends AbstractIT {

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private static final String USER_KERMIT = "kermit";
  private static final String TEST_GROUP = "testGroup";
  private static final String TEST_GROUP_B = "anotherTestGroup";

  @Test
  public void verifySyncEnabledByDefault() {
    assertThat(getUserIdentityCacheService().isScheduledToRun()).isTrue();
  }

  @Test
  public void testSyncStoppedSuccessfully() {
    try {
      getUserIdentityCacheService().stopScheduledSync();
      assertThat(getUserIdentityCacheService().isScheduledToRun()).isFalse();
    } finally {
      getUserIdentityCacheService().startScheduledSync();
    }
  }

  @Test
  public void testCacheReplacedOnNewSync() {
    try {
      // given
      getUserIdentityCacheService().stopScheduledSync();
      authorizationClient.addKermitUserAndGrantAccessToOptimize();
      getUserIdentityCacheService().synchronizeIdentities();

      // when
      final String userIdJohn = "john";
      authorizationClient.addUserAndGrantOptimizeAccess(userIdJohn);
      getUserIdentityCacheService().synchronizeIdentities();

      // then
      assertThat(getUserIdentityCacheService().getUserIdentityById(userIdJohn)).isPresent();
    } finally {
      getUserIdentityCacheService().startScheduledSync();
    }
  }

  @Test
  public void testCacheNotReplacedOnLimitHit() {
    try {
      // given
      getUserIdentityCacheService().stopScheduledSync();
      authorizationClient.addKermitUserAndGrantAccessToOptimize();
      getUserIdentityCacheService().synchronizeIdentities();

      // when
      final String userIdJohn = "john";
      authorizationClient.addUserAndGrantOptimizeAccess(userIdJohn);
      // we have at least two users, but limit is now 1
      getIdentitySyncConfiguration().setMaxEntryLimit(1L);

      // then
      final UserIdentityCacheService userIdentityCacheService = getUserIdentityCacheService();
      assertThatThrownBy(userIdentityCacheService::synchronizeIdentities)
        .isInstanceOf(MaxEntryLimitHitException.class);
      assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
      assertThat(getUserIdentityCacheService().getUserIdentityById(userIdJohn)).isNotPresent();
    } finally {
      getUserIdentityCacheService().startScheduledSync();
    }
  }

  @Test
  public void testGrantedUserIsImportedMetaDataAvailable() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    final Optional<UserDto> userIdentityById = getUserIdentityCacheService().getUserIdentityById(KERMIT_USER);
    assertThat(userIdentityById).isPresent();
    assertThat(userIdentityById.get().getName()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(userIdentityById.get().getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
    assertThat(userIdentityById.get().getLastName()).isEqualTo(DEFAULT_LASTNAME);
    assertThat(userIdentityById.get().getEmail()).contains(DEFAULT_EMAIL_DOMAIN);
  }

  @Test
  public void testGrantedUserIsImportedMetaNotAvailableIfDisabled() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    getIdentitySyncConfiguration().setIncludeUserMetaData(false);

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    final Optional<UserDto> userIdentityById = getUserIdentityCacheService().getUserIdentityById(KERMIT_USER);
    assertThat(userIdentityById).isPresent();
    assertThat(userIdentityById.get().getName()).isEqualTo(KERMIT_USER);
    assertThat(userIdentityById.get().getFirstName()).isNull();
    assertThat(userIdentityById.get().getLastName()).isNull();
    assertThat(userIdentityById.get().getEmail()).isNull();
  }

  @Test
  public void testNotGrantedUserIsNotImported() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
  }

  @Test
  public void testGrantedGroupIsImported() {
    // given
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    final Optional<GroupDto> groupIdentityById = getUserIdentityCacheService().getGroupIdentityById(GROUP_ID);
    assertThat(groupIdentityById).isPresent();
    assertThat(groupIdentityById.get().getName()).isEqualTo(KERMIT_GROUP_NAME);
    assertThat(groupIdentityById.get().getMemberCount()).isEqualTo(1L);
  }

  @Test
  public void testNotGrantedGroupIsNotImported() {
    // given
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getGroupIdentityById(GROUP_ID)).isNotPresent();
  }

  @Test
  public void testGrantedGroupMemberIsImported() {
    // given
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
  }

  @Test
  public void testNotGrantedGroupMemberIsNotImported() {
    // given
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
  }

  @Test
  public void testGrantedGroupMemberIsImportedAlthoughAlsoMemberOfNotGrantedGroup() {
    // given
    // https://docs.camunda.org/manual/7.11/user-guide/process-engine/authorization-service/#authorization-precedence
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    final String revokedGroupId = "revokedGroup";
    authorizationClient.createGroupAndAddUser(revokedGroupId, KERMIT_USER);
    authorizationClient.revokeSingleResourceAuthorizationsForGroup(
      revokedGroupId, OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
  }

  @Test
  public void testRevokedUserIsNotImportedAlthoughMemberOfGrantedGroup() {
    // given
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
  }

  @Test
  public void testGlobalAuthNoExplicitGrantUserIsImported() {
    // given
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isPresent();
  }

  @Test
  public void testGlobalAuthRevokedAuthUserIsNotImported() {
    // given
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
  }

  @Test
  public void testGlobalAuthNoExplicitGrantGroupIsImported() {
    // given
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getGroupIdentityById(GROUP_ID)).isPresent();
  }

  @Test
  public void testGlobalAuthRevokedAuthGroupIsNotImported() {
    // given
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getGroupIdentityById(GROUP_ID)).isNotPresent();
  }

  @Test
  public void testGlobalAuthRevokedAuthGroupMemberIsNotImported() {
    // given
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    // when
    getUserIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserIdentityCacheService().getUserIdentityById(KERMIT_USER)).isNotPresent();
  }

  @Test
  public void testPermissionCleanupAfterIdentitySync() {
    final UserIdentityCacheService userIdentityCacheService = getUserIdentityCacheService();
    try {
      // given a collection with permissions for users/groups
      userIdentityCacheService.stopScheduledSync();

      authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
      authorizationClient.addKermitUserWithoutAuthorizations();
      authorizationClient.addUserAndGrantOptimizeAccess(DEFAULT_USERNAME);
      authorizationClient.createGroupAndAddUser(TEST_GROUP, USER_KERMIT);
      authorizationClient.createGroupAndAddUser(TEST_GROUP_B, DEFAULT_USERNAME);

      userIdentityCacheService.synchronizeIdentities();

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

      userIdentityCacheService.synchronizeIdentities();

      // then users/groups no longer existing in identityCache have been removed from the collection's permissions
      List<IdResponseDto> roleIds1 = collectionClient.getCollectionRoleIdDtos(collectionId1);
      List<IdResponseDto> roleIds2 = collectionClient.getCollectionRoleIdDtos(collectionId2);
      assertThat(roleIds1).containsExactlyInAnyOrderElementsOf(roleIds2);
      assertThat(roleIds1).containsExactlyInAnyOrder(
        new IdResponseDto(testGroupBRole.getId()),
        new IdResponseDto(userDemoRole.getId())
      );
    } finally {
      userIdentityCacheService.startScheduledSync();
    }
  }

  @Test
  public void testPermissionCleanupAfterIdentitySyncRemovesLastManager() {
    final UserIdentityCacheService userIdentityCacheService = getUserIdentityCacheService();
    try {
      // given
      userIdentityCacheService.startScheduledSync();

      authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_APPLICATION);
      authorizationClient.addKermitUserWithoutAuthorizations();
      embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(DEFAULT_USERNAME);

      userIdentityCacheService.synchronizeIdentities();

      final String collectionId = collectionClient.createNewCollection(USER_KERMIT, USER_KERMIT);

      // when
      authorizationClient.revokeSingleResourceAuthorizationsForUser(
        USER_KERMIT,
        OPTIMIZE_APPLICATION_RESOURCE_ID,
        RESOURCE_TYPE_APPLICATION
      );

      userIdentityCacheService.synchronizeIdentities();

      // then
      List<CollectionRoleResponseDto> roles = collectionClient.getCollectionRoles(collectionId);
      assertThat(roles).isEmpty();
    } finally {
      userIdentityCacheService.startScheduledSync();
    }
  }

  private UserIdentityCacheService getUserIdentityCacheService() {
    return embeddedOptimizeExtension.getUserIdentityCacheService();
  }

  private UserIdentityCacheConfiguration getIdentitySyncConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getUserIdentityCacheConfiguration();
  }
}
