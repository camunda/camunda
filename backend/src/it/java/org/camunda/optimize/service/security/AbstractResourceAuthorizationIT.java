/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;

public abstract class AbstractResourceAuthorizationIT {
  public static final String KERMIT_USER = "kermit";
  public static final String GROUP_ID = "kermitGroup";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();

  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  protected void addKermitUserAndGrantAccessToOptimize() {
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
  }

  protected void createKermitGroupAndAddKermitToThatGroup() {
    engineRule.createGroup(GROUP_ID, "Group", "foo");
    engineRule.addUserToGroup(KERMIT_USER, GROUP_ID);
  }

  protected void addGlobalAuthorizationForResource(final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId(ALL_RESOURCES_RESOURCE_ID);
    engineRule.createAuthorization(authorizationDto);
  }

  protected void grantAllResourceAuthorizationsForKermitGroup(final int resourceType) {
    grantAllResourceAuthorizationsForGroup(GROUP_ID, resourceType);
  }

  protected void grantSingleResourceAuthorizationForKermitGroup(final String resourceId, final int resourceType) {
    grantSingleResourceAuthorizationsForGroup(GROUP_ID, resourceId, resourceType);
  }

  protected void revokeAllDefinitionAuthorizationsForKermitGroup(final int resourceType) {
    revokeAllDefinitionAuthorizationsForGroup(GROUP_ID, resourceType);
  }

  protected void revokeSingleDefinitionAuthorizationsForKermitGroup(final String resourceID, final int resourceType) {
    revokeSingleResourceAuthorizationsForGroup(GROUP_ID, resourceID, resourceType);
  }

  protected void grantAllResourceAuthorizationsForGroup(final String groupId, final int resourceType) {
    grantSingleResourceAuthorizationsForGroup(groupId, ALL_RESOURCES_RESOURCE_ID, resourceType);
  }

  protected void grantSingleResourceAuthorizationsForGroup(final String groupId,
                                                           final String resourceId,
                                                           final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  protected void revokeAllDefinitionAuthorizationsForGroup(final String groupId, final int definitionResourceType) {
    revokeSingleResourceAuthorizationsForGroup(groupId, ALL_RESOURCES_RESOURCE_ID, definitionResourceType);
  }

  protected void revokeSingleResourceAuthorizationsForGroup(final String groupId,
                                                            final String resourceId,
                                                            final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  protected void grantAllResourceAuthorizationsForKermit(final int resourceType) {
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, ALL_RESOURCES_RESOURCE_ID, resourceType);
  }

  protected void grantSingleResourceAuthorizationForKermit(final String resourceId, final int resourceType) {
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, resourceId, resourceType);
  }

  protected void grantSingleResourceAuthorizationsForUser(final String userId,
                                                          final String resourceId,
                                                          final int resourceType) {
    grantSingleResourceAuthorizationsForUser(
      userId, Collections.singletonList(ALL_PERMISSION), resourceId, resourceType
    );
  }

  protected void grantAllDefinitionAuthorizationsForUserWithReadHistoryPermission(final String userId,
                                                                                  final int definitionResourceType) {
    grantSingleResourceAuthorizationsForUser(
      userId, ImmutableList.of(READ_HISTORY_PERMISSION), ALL_RESOURCES_RESOURCE_ID, definitionResourceType
    );
  }

  protected void grantSingleResourceAuthorizationsForUser(final String userId,
                                                          final List<String> permissions,
                                                          final String resourceId,
                                                          final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  protected void revokeAllResourceAuthorizationsForKermit(final int resourceType) {
    revokeAllResourceAuthorizationsForUser(KERMIT_USER, resourceType);
  }

  protected void revokeSingleResourceAuthorizationsForKermit(final String resourceId, final int resourceType) {
    revokeSingleResourceAuthorizationsForUser(KERMIT_USER, resourceId, resourceType);
  }

  protected void revokeAllResourceAuthorizationsForUser(final String userId, final int resourceType) {
    revokeSingleResourceAuthorizationsForUser(userId, ALL_RESOURCES_RESOURCE_ID, resourceType);
  }

  protected void revokeSingleResourceAuthorizationsForUser(final String userId,
                                                           final String definitionKey,
                                                           final int resourceType) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(resourceType);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

}
