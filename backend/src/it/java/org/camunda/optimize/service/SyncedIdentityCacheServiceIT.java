/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.service.util.configuration.engine.UserSyncConfiguration;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule.KERMIT_GROUP_NAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SyncedIdentityCacheServiceIT {
  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchExtension =
    new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineExtension = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtension = new EmbeddedOptimizeExtensionRule();
  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtensionRule engineDatabaseExtension = new EngineDatabaseExtensionRule(
    engineExtension.getEngineName()
  );

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineExtension);

  @Test
  public void verifySyncEnabledByDefault() {
    assertThat(getUserSyncConfiguration().isEnabled(), is(true));
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
      getUserSyncConfiguration().setMaxEntryLimit(1L);
      getSyncedIdentityCacheService().synchronizeIdentities();

      // then
      assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(true));
      assertThat(getSyncedIdentityCacheService().getUserIdentityById(userIdJohn).isPresent(), is(false));
    } finally {
      getSyncedIdentityCacheService().startSchedulingUserSync();
    }
  }

  @Test
  public void testGrantedUserIsImported() {
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
  public void testNotGrantedUserIsNotImported() {
    engineExtension.addUser(KERMIT_USER, KERMIT_USER);

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
    authorizationClient.revokeSingleDefinitionAuthorizationsForKermitGroup(
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
    authorizationClient.revokeSingleDefinitionAuthorizationsForKermitGroup(
      OPTIMIZE_APPLICATION_RESOURCE_ID, RESOURCE_TYPE_APPLICATION
    );

    final SyncedIdentityCacheService syncedIdentityCacheService = getSyncedIdentityCacheService();
    syncedIdentityCacheService.synchronizeIdentities();

    assertThat(getSyncedIdentityCacheService().getUserIdentityById(KERMIT_USER).isPresent(), is(false));
  }

  private SyncedIdentityCacheService getSyncedIdentityCacheService() {
    return embeddedOptimizeExtension.getSyncedIdentityCacheService();
  }

  private UserSyncConfiguration getUserSyncConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getUserSyncConfiguration();
  }

}
