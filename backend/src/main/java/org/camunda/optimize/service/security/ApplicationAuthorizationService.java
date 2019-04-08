/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ACCESS_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;

@Component
public class ApplicationAuthorizationService {

  /**
   * Checks for a given engine if the user is authorized for optimize access.
   * 
   * @return True, if and only if there is a global grant (but no user/group
   *         revoke) or a group grant (but no user revoke) or a user grant.
   *         Notice that this implies that false is returned also if there is no
   *         grant nor revoke.
   */
  public boolean isAuthorizedToAccessOptimize(String username, EngineContext engineContext) {
    return performAuthorizationCheck(username, engineContext);
  }

  private boolean performAuthorizationCheck(String username, EngineContext engineContext) {
    List<GroupDto> groupsOfUser = engineContext.getAllGroupsOfUser(username);
    List<AuthorizationDto> allApplicationAuthorizations = engineContext.getAllApplicationAuthorizations();
    List<AuthorizationDto> groupAuthorizations =
      extractGroupAuthorizations(groupsOfUser, allApplicationAuthorizations);
    List<AuthorizationDto> userAuthorizations =
      extractUserAuthorizations(username, allApplicationAuthorizations);

    // NOTE: the order is essential here to make sure that
    // the revoking of permission works correctly
    boolean isAuthorized = checkIfGlobalUsageOfOptimizeIsGranted(allApplicationAuthorizations);
    // group authorizations
    isAuthorized &= !doesAnyGroupRevokeAuthorizationForAllResources(groupAuthorizations);
    isAuthorized |= doesAnyGroupGrantAuthorizationForAllResources(groupAuthorizations);
    isAuthorized &= !doesAnyGroupRevokeAuthorizationForOptimize(groupAuthorizations);
    isAuthorized |= doesAnyGroupGrantAuthorizationForOptimize(groupAuthorizations);
    // user authorizations
    isAuthorized &= !isUserAuthorizationForAllResourcesRevoked(userAuthorizations);
    isAuthorized |= isUserAuthorizationForAllResourcesGranted(userAuthorizations);
    isAuthorized &= !isUserAuthorizationForOptimizeRevoked(userAuthorizations);
    isAuthorized |= isUserAuthorizationForOptimizeGranted(userAuthorizations);

    return isAuthorized;
  }

  private List<AuthorizationDto> extractGroupAuthorizations(List<GroupDto> groupsOfUser,
                                                            List<AuthorizationDto> allAuthorizations) {
    Set<String> groupIds = groupsOfUser.stream().map(GroupDto::getId).collect(Collectors.toSet());
    return allAuthorizations
      .stream()
      .filter(a -> groupIds.contains(a.getGroupId()))
      .collect(Collectors.toList());
  }

  private List<AuthorizationDto> extractUserAuthorizations(String username,
                                                           List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations
      .stream()
      .filter(a -> username.equals(a.getUserId()))
      .collect(Collectors.toList());
  }

  private boolean checkIfGlobalUsageOfOptimizeIsGranted(List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations.stream().anyMatch(this::grantsGloballyToUseOptimize);
  }

  private boolean isUserAuthorizationForAllResourcesGranted(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> grantsToUseOptimize(a, ALL_RESOURCES_RESOURCE_ID)
    );
  }

  private boolean isUserAuthorizationForOptimizeGranted(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> grantsToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID)
    );
  }

  private boolean isUserAuthorizationForAllResourcesRevoked(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> revokesToUseOptimize(a, ALL_RESOURCES_RESOURCE_ID)
    );
  }

  private boolean isUserAuthorizationForOptimizeRevoked(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> revokesToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID)
    );
  }

  private boolean doesAnyGroupGrantAuthorizationForOptimize(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> grantsToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID));
  }

  private boolean doesAnyGroupGrantAuthorizationForAllResources(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> grantsToUseOptimize(a, ALL_RESOURCES_RESOURCE_ID));
  }

  private boolean doesAnyGroupRevokeAuthorizationForOptimize(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> revokesToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID));
  }

  private boolean doesAnyGroupRevokeAuthorizationForAllResources(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> revokesToUseOptimize(a, ALL_RESOURCES_RESOURCE_ID));
  }

  private boolean grantsToUseOptimize(AuthorizationDto a, String resource) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean optimizeResourceId = a.getResourceId().toLowerCase().trim().equals(resource);
    return hasPermissions && grantPermission && optimizeResourceId;
  }

  private boolean revokesToUseOptimize(AuthorizationDto a, String resource) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean optimizeResourceId = a.getResourceId().toLowerCase().trim().equals(resource);
    return hasPermissions && grantPermission && optimizeResourceId;
  }

  private boolean grantsGloballyToUseOptimize(AuthorizationDto a) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GLOBAL;
    boolean optimizeResourceId =
      a.getResourceId().toLowerCase().equals(OPTIMIZE_APPLICATION_RESOURCE_ID) ||
        a.getResourceId().trim().equals(ALL_RESOURCES_RESOURCE_ID);
    return hasPermissions && grantPermission && optimizeResourceId;
  }

}
