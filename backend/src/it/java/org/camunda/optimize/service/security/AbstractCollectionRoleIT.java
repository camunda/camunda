/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public abstract class AbstractCollectionRoleIT {
  protected EngineIntegrationRule engineRule = new EngineIntegrationRule();
  protected ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  protected EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineRule);

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  protected static final String ACCESS_ONLY_ROLES = "accessOnlyRoles";

  protected static RoleType[] accessOnlyRoles() {
    return new RoleType[]{RoleType.VIEWER};
  }

  protected static final String EDIT_ROLES = "editRoles";

  protected static RoleType[] editRoles() {
    return new RoleType[]{RoleType.EDITOR, RoleType.MANAGER};
  }

  protected static final String NON_MANAGER_ROLES = "nonManagerRoles";

  private static RoleType[] nonManagerRoles() {
    return new RoleType[]{RoleType.VIEWER, RoleType.EDITOR};
  }

  protected static final String MANAGER_ROLES = "managerRoles";

  private static RoleType[] managerRoles() {
    return new RoleType[]{RoleType.MANAGER};
  }

  protected static final String ACCESS_IDENTITY_ROLES = "accessIdentityRoles";

  protected static IdentityAndRole[] accessIdentityRoles() {
    return Arrays.stream(RoleType.values())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(
          new IdentityDto(getDefaultIdentityIdForType(identityType), identityType),
          roleType
        ))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String ACCESS_USER_ROLES = "accessUserRoles";

  protected static IdentityAndRole[] accessUserRoles() {
    return Arrays.stream(RoleType.values())
      .map(roleType -> new IdentityAndRole(
        new IdentityDto(getDefaultIdentityIdForType(IdentityType.USER), IdentityType.USER), roleType
      ))
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String ACCESS_ONLY_IDENTITY_ROLES = "accessOnlyIdentityRoles";

  protected static IdentityAndRole[] accessOnlyIdentityRoles() {
    return Arrays.stream(accessOnlyRoles())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(
          new IdentityDto(getDefaultIdentityIdForType(identityType), identityType),
          roleType
        ))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String ACCESS_ONLY_USER_ROLES = "accessOnlyUserRoles";

  protected static IdentityAndRole[] accessOnlyUserRoles() {
    return Arrays.stream(accessOnlyRoles())
      .map(roleType -> new IdentityAndRole(
        new IdentityDto(getDefaultIdentityIdForType(IdentityType.USER), IdentityType.USER), roleType
      ))
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String EDIT_IDENTITY_ROLES = "editIdentityRoles";

  protected static IdentityAndRole[] editIdentityRoles() {
    return Arrays.stream(editRoles())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(
          new IdentityDto(getDefaultIdentityIdForType(identityType), identityType),
          roleType
        ))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String EDIT_USER_ROLES = "editUserRoles";

  protected static IdentityAndRole[] editUserRoles() {
    return Arrays.stream(editRoles())
      .map(roleType -> new IdentityAndRole(
        new IdentityDto(getDefaultIdentityIdForType(IdentityType.USER), IdentityType.USER), roleType
      ))
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String EDIT_IDENITY_ROLES_AND_RESOURCE_SCENARIOS = "editIdentityRolesAndResourceScenarios";

  protected static IdentityRoleAndResource[] editIdentityRolesAndResourceScenarios() {
    return Arrays.stream(editIdentityRoles())
      .flatMap(AbstractCollectionRoleIT::createResourceScenariosForRoleType)
      .toArray(IdentityRoleAndResource[]::new);
  }

  protected static final String EDIT_USER_ROLES_AND_RESOURCE_SCENARIOS = "editUserRolesAndResourceScenarios";

  protected static IdentityRoleAndResource[] editUserRolesAndResourceScenarios() {
    return Arrays.stream(editUserRoles())
      .flatMap(AbstractCollectionRoleIT::createResourceScenariosForRoleType)
      .toArray(IdentityRoleAndResource[]::new);
  }

  protected static final String MANAGER_IDENTITY_ROLES = "managerIdentityRoles";

  protected static IdentityAndRole[] managerIdentityRoles() {
    return Arrays.stream(managerRoles())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(
          new IdentityDto(getDefaultIdentityIdForType(identityType), identityType),
          roleType
        ))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String NON_MANAGER_IDENTITY_ROLES = "nonManagerIdentityRoles";

  protected static IdentityAndRole[] nonManagerIdentityRoles() {
    return Arrays.stream(nonManagerRoles())
      .flatMap(roleType -> Arrays.stream(IdentityType.values())
        .map(identityType -> new IdentityAndRole(
          new IdentityDto(getDefaultIdentityIdForType(identityType), identityType),
          roleType
        ))
      )
      .toArray(IdentityAndRole[]::new);
  }

  protected static final String ACCESS_ONLY_IDENTITY_ROLES_AND_RESOURCE_SCENARIOS =
    "accessOnlyIdentityRolesAndResourceScenarios";

  protected static IdentityRoleAndResource[] accessOnlyIdentityRolesAndResourceScenarios() {
    return Arrays.stream(accessOnlyIdentityRoles())
      .flatMap(AbstractCollectionRoleIT::createResourceScenariosForRoleType)
      .toArray(IdentityRoleAndResource[]::new);
  }

  protected static final String ACCESS_ONLY_USER_ROLES_AND_RESOURCE_SCENARIOS =
    "accessOnlyUserRolesAndResourceScenarios";

  protected static IdentityRoleAndResource[] accessOnlyUserRolesAndResourceScenarios() {
    return Arrays.stream(accessOnlyUserRoles())
      .flatMap(AbstractCollectionRoleIT::createResourceScenariosForRoleType)
      .toArray(IdentityRoleAndResource[]::new);
  }

  protected static Stream<IdentityRoleAndResource> createResourceScenariosForRoleType(
    final IdentityAndRole identityRolePair) {
    return Stream.of(
      new IdentityRoleAndResource(identityRolePair, EntityType.REPORT, ReportType.PROCESS, false),
      new IdentityRoleAndResource(identityRolePair, EntityType.REPORT, ReportType.PROCESS, true),
      new IdentityRoleAndResource(identityRolePair, EntityType.REPORT, ReportType.DECISION, false),
      new IdentityRoleAndResource(identityRolePair, EntityType.DASHBOARD, null, false)
    );
  }

  protected static String getDefaultIdentityIdForType(final IdentityType identityType) {
    return identityType.equals(IdentityType.USER) ? KERMIT_USER : GROUP_ID;
  }

  protected OptimizeRequestExecutor getOptimizeRequestExecutorWithKermitAuthentication() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER);
  }

  protected void addKermitGroupRoleToCollectionAsDefaultUser(final RoleType roleType, final String collectionId) {
    addRoleToCollectionAsDefaultUser(
      collectionId, new CollectionRoleDto(new IdentityDto(GROUP_ID, IdentityType.GROUP), roleType)
    );
  }

  protected void addRoleToCollectionAsDefaultUser(final RoleType roleType,
                                                  final IdentityDto identityDto,
                                                  final String collectionId) {
    addRoleToCollectionAsDefaultUser(collectionId, new CollectionRoleDto(identityDto, roleType));
  }

  protected String addRoleToCollectionAsDefaultUser(final String collectionId, final CollectionRoleDto roleDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  protected String createNewCollectionAsDefaultUser() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  @Data
  @AllArgsConstructor
  protected static class IdentityRoleAndResource {
    IdentityAndRole identityAndRole;
    EntityType entityType;
    ReportType reportType;
    boolean combined;
  }

  @Data
  @AllArgsConstructor
  protected static class IdentityAndRole {
    IdentityDto identityDto;
    RoleType roleType;
  }

}
