/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.engine;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_HISTORY_PERMISSION;

@AllArgsConstructor
@Builder
public class AuthorizationClient {
  public static final String KERMIT_USER = "kermit";
  public static final String GROUP_ID = "kermitGroup";

  private final EngineIntegrationExtension engineExtension;

  public void addKermitUserWithoutAuthorizations() {
    engineExtension.addUser(KERMIT_USER, KERMIT_USER);
  }

  public void addKermitUserAndGrantAccessToOptimize() {
    addUserAndGrantOptimizeAccess(KERMIT_USER);
  }

  public void addUserAndGrantOptimizeAccess(final String userId) {
    engineExtension.addUser(userId, userId);
    engineExtension.grantUserOptimizeAccess(userId);
  }

  public void createGroupAndGrantOptimizeAccess(final String groupId, final String groupName) {
    engineExtension.createGroup(groupId, groupName);
    engineExtension.grantGroupOptimizeAccess(groupId);
  }

  public void createKermitGroupAndAddKermitToThatGroup() {
    createGroupAndAddUser(GROUP_ID, KERMIT_USER);
  }

  public void createGroupAndAddUser(final String groupId, final String userId) {
    engineExtension.createGroup(groupId);
    engineExtension.addUserToGroup(userId, groupId);
  }

  public void createGroupAndAddUsers(final String groupId, final String... userIds) {
    engineExtension.createGroup(groupId);
    Arrays.asList(userIds).forEach(userId -> engineExtension.addUserToGroup(userId, groupId));
  }

  public void grantKermitGroupOptimizeAccess() {
    engineExtension.grantGroupOptimizeAccess(GROUP_ID);
  }

  public void grantGroupOptimizeAccess(final String groupId) {
    engineExtension.grantGroupOptimizeAccess(groupId);
  }

  public void addGlobalAuthorizationForResource(final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId(ALL_RESOURCES_RESOURCE_ID);
    engineExtension.createAuthorization(authorizationDto);
  }

  public void grantAllResourceAuthorizationsForKermitGroup(final int resourceType) {
    grantSingleResourceAuthorizationsForGroup(GROUP_ID, ALL_RESOURCES_RESOURCE_ID, resourceType);
  }

  public void grantSingleResourceAuthorizationForKermitGroup(final String resourceId, final int resourceType) {
    grantSingleResourceAuthorizationsForGroup(GROUP_ID, resourceId, resourceType);
  }

  public void revokeAllDefinitionAuthorizationsForKermitGroup(final int resourceType) {
    revokeSingleResourceAuthorizationsForGroup(GROUP_ID, ALL_RESOURCES_RESOURCE_ID, resourceType);
  }

  public void revokeSingleResourceAuthorizationsForKermitGroup(final String resourceId, final int resourceType) {
    revokeSingleResourceAuthorizationsForGroup(GROUP_ID, resourceId, resourceType);
  }

  public void grantSingleResourceAuthorizationsForGroup(final String groupId,
                                                        final String resourceId,
                                                        final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId(groupId);
    engineExtension.createAuthorization(authorizationDto);
  }

  public void revokeSingleResourceAuthorizationsForGroup(final String groupId,
                                                         final String resourceId,
                                                         final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId(groupId);
    engineExtension.createAuthorization(authorizationDto);
  }

  public void grantAllResourceAuthorizationsForKermit(final int resourceType) {
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, ALL_RESOURCES_RESOURCE_ID, resourceType);
  }

  public void grantSingleResourceAuthorizationForKermit(final String resourceId, final int resourceType) {
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, resourceId, resourceType);
  }

  public void grantSingleResourceAuthorizationsForUser(final String userId,
                                                       final String resourceId,
                                                       final int resourceType) {
    grantSingleResourceAuthorizationsForUser(
      userId, Collections.singletonList(ALL_PERMISSION), resourceId, resourceType
    );
  }

  public void grantAllDefinitionAuthorizationsForUserWithReadHistoryPermission(final String userId,
                                                                               final int definitionResourceType) {
    grantSingleResourceAuthorizationsForUser(
      userId, ImmutableList.of(READ_HISTORY_PERMISSION), ALL_RESOURCES_RESOURCE_ID, definitionResourceType
    );
  }

  public void grantSingleResourceAuthorizationsForUser(final String userId,
                                                       final List<String> permissions,
                                                       final String resourceId,
                                                       final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineExtension.createAuthorization(authorizationDto);
  }

  public void revokeAllResourceAuthorizationsForKermit(final int resourceType) {
    revokeAllResourceAuthorizationsForUser(KERMIT_USER, resourceType);
  }

  public void revokeSingleResourceAuthorizationsForKermit(final String resourceId, final int resourceType) {
    revokeSingleResourceAuthorizationsForUser(KERMIT_USER, resourceId, resourceType);
  }

  public void revokeAllResourceAuthorizationsForUser(final String userId, final int resourceType) {
    revokeSingleResourceAuthorizationsForUser(userId, ALL_RESOURCES_RESOURCE_ID, resourceType);
  }

  public void revokeSingleResourceAuthorizationsForUser(final String userId,
                                                        final String definitionKey,
                                                        final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId(userId);
    engineExtension.createAuthorization(authorizationDto);
  }

}
