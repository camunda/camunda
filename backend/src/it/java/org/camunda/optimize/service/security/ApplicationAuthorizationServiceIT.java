/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ApplicationAuthorizationServiceIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void grantAccessOnGlobally() {
    // given
    engineIntegrationExtensionRule.addUser("genzo", "genzo");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("genzo", "genzo");
  }

  @Test
  public void grantAccessForGroupForAllResources() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.createGroup("kermitGroup");
    engineIntegrationExtensionRule.addUserToGroup("kermit", "kermitGroup");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForGroupForOptimizeResource() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.createGroup("kermitGroup");
    engineIntegrationExtensionRule.addUserToGroup("kermit", "kermitGroup");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForUserForAllResources() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForUserForOptimizeResource() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void revokeAccessForGroupForAllResources() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.createGroup("kermitGroup");
    engineIntegrationExtensionRule.addUserToGroup("kermit", "kermitGroup");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermitGroup");
    authorizationDto.setUserId(null);
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForGroupForOptimizeResource() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.createGroup("kermitGroup");
    engineIntegrationExtensionRule.addUserToGroup("kermit", "kermitGroup");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setGroupId("kermitGroup");
    authorizationDto.setUserId(null);
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForUserForAllResources() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.createGroup("kermitGroup");
    engineIntegrationExtensionRule.addUserToGroup("kermit", "kermitGroup");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForUserForOptimizeResource() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.createGroup("kermitGroup");
    engineIntegrationExtensionRule.addUserToGroup("kermit", "kermitGroup");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void grantRevokeGrantRevokeAccess() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.createGroup("kermitGroup");
    engineIntegrationExtensionRule.addUserToGroup("kermit", "kermitGroup");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermitGroup");
    authorizationDto.setUserId(null);
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");

    // when
    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");

    // when
    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessIsSpecificToUsers() {
    // given
    engineIntegrationExtensionRule.addUser("genzo", "genzo");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("admin");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("genzo", "genzo");
  }

  @Test
  public void addGroupAccessAndRevokeAccessForGroupForOptimizeResource() {
    // given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.createGroup("kermitGroup");
    engineIntegrationExtensionRule.addUserToGroup("kermit", "kermitGroup");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId("optimize");
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }


  private void validateUserIsNotAuthorized(String user, String password) {
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest(user, password);

    assertThat(response.getStatus(),is(403));
  }
  private void validateThatUserCanBeAuthenticated(String user, String password) {
    Response response = embeddedOptimizeExtensionRule.authenticateUserRequest(user, password);

    assertThat(response.getStatus(),is(200));
  }
}
