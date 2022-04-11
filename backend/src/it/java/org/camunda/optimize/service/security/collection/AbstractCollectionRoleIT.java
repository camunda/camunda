/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.test.engine.AuthorizationClient;

import java.util.Arrays;

import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public abstract class AbstractCollectionRoleIT extends AbstractIT {

  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  protected static RoleType[] accessOnlyRoles() {
    return new RoleType[]{RoleType.VIEWER};
  }

  protected static RoleType[] editRoles() {
    return new RoleType[]{RoleType.EDITOR, RoleType.MANAGER};
  }

  private static RoleType[] nonManagerRoles() {
    return new RoleType[]{RoleType.VIEWER, RoleType.EDITOR};
  }

  private static RoleType[] managerRoles() {
    return new RoleType[]{RoleType.MANAGER};
  }

  protected static final String ACCESS_IDENTITY_ROLES = "accessIdentityRoles";

  protected static IdentityAndRole[] accessIdentityRoles() {
    return Arrays.stream(RoleType.values())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(getDefaultIdentityDtoForType(identityType), roleType))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String ACCESS_ONLY_IDENTITY_ROLES = "accessOnlyIdentityRoles";

  protected static IdentityAndRole[] accessOnlyIdentityRoles() {
    return Arrays.stream(accessOnlyRoles())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(getDefaultIdentityDtoForType(identityType), roleType))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String ACCESS_ONLY_USER_ROLES = "accessOnlyUserRoles";

  protected static IdentityAndRole[] accessOnlyUserRoles() {
    return Arrays.stream(accessOnlyRoles())
      .map(roleType -> new IdentityAndRole(getDefaultIdentityDtoForType(IdentityType.USER), roleType))
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String EDIT_IDENTITY_ROLES = "editIdentityRoles";

  protected static IdentityAndRole[] editIdentityRoles() {
    return Arrays.stream(editRoles())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(getDefaultIdentityDtoForType(identityType), roleType))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String EDIT_USER_ROLES = "editUserRoles";

  protected static IdentityAndRole[] editUserRoles() {
    return Arrays.stream(editRoles())
      .map(roleType -> new IdentityAndRole(getDefaultIdentityDtoForType(IdentityType.USER), roleType))
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String MANAGER_IDENTITY_ROLES = "managerIdentityRoles";

  protected static IdentityAndRole[] managerIdentityRoles() {
    return Arrays.stream(managerRoles())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(getDefaultIdentityDtoForType(identityType), roleType))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String NON_MANAGER_IDENTITY_ROLES = "nonManagerIdentityRoles";

  protected static IdentityAndRole[] nonManagerIdentityRoles() {
    return Arrays.stream(nonManagerRoles())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(getDefaultIdentityDtoForType(identityType), roleType))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static IdentityDto getDefaultIdentityDtoForType(final IdentityType identityType) {
    return identityType.equals(IdentityType.USER)
      ? new IdentityDto(KERMIT_USER, identityType)
      : new IdentityDto(GROUP_ID, identityType);
  }

  protected RoleType getExpectedResourceRoleForCollectionRole(final IdentityAndRole identityAndRole) {
    return identityAndRole.roleType == RoleType.MANAGER ? RoleType.EDITOR : identityAndRole.roleType;
  }

  protected OptimizeRequestExecutor getOptimizeRequestExecutorWithKermitAuthentication() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER);
  }

  protected void addKermitGroupRoleToCollectionAsDefaultUser(final RoleType roleType, final String collectionId) {
    collectionClient.addRolesToCollection(
      collectionId,
      new CollectionRoleRequestDto(new IdentityDto(GROUP_ID, IdentityType.GROUP), roleType)
    );
  }

  protected void addRoleToCollectionAsDefaultUser(final RoleType roleType,
                                                  final IdentityDto identityDto,
                                                  final String collectionId) {
    collectionClient.addRolesToCollection(collectionId, new CollectionRoleRequestDto(identityDto, roleType));
  }

  @Data
  @AllArgsConstructor
  protected static class IdentityAndRole {
    IdentityDto identityDto;
    RoleType roleType;
  }

}
