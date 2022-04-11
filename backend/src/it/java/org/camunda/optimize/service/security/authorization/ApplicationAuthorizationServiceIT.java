/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.importing.EngineConstants.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_APPLICATION;

public class ApplicationAuthorizationServiceIT extends AbstractIT {

  @Test
  public void grantAccessOnGlobally() {
    // given
    engineIntegrationExtension.addUser("genzo", "genzo");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("genzo", "genzo");
  }

  @Test
  public void grantAccessForGroupForAllResources() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.createGroup("kermitGroup");
    engineIntegrationExtension.addUserToGroup("kermit", "kermitGroup");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForGroupForOptimizeResource() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.createGroup("kermitGroup");
    engineIntegrationExtension.addUserToGroup("kermit", "kermitGroup");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForUserForAllResources() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForUserForOptimizeResource() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void revokeAccessForGroupForAllResources() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.createGroup("kermitGroup");
    engineIntegrationExtension.addUserToGroup("kermit", "kermitGroup");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineIntegrationExtension.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermitGroup");
    authorizationDto.setUserId(null);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForGroupForOptimizeResource() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.createGroup("kermitGroup");
    engineIntegrationExtension.addUserToGroup("kermit", "kermitGroup");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineIntegrationExtension.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setGroupId("kermitGroup");
    authorizationDto.setUserId(null);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForUserForAllResources() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.createGroup("kermitGroup");
    engineIntegrationExtension.addUserToGroup("kermit", "kermitGroup");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtension.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForUserForOptimizeResource() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.createGroup("kermitGroup");
    engineIntegrationExtension.addUserToGroup("kermit", "kermitGroup");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtension.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void grantRevokeGrantRevokeAccess() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.createGroup("kermitGroup");
    engineIntegrationExtension.addUserToGroup("kermit", "kermitGroup");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermitGroup");
    authorizationDto.setUserId(null);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");

    // when
    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");

    // when
    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessIsSpecificToUsers() {
    // given
    engineIntegrationExtension.addUser("genzo", "genzo");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("admin");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("genzo", "genzo");
  }

  @Test
  public void addGroupAccessAndRevokeAccessForGroupForOptimizeResource() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.createGroup("kermitGroup");
    engineIntegrationExtension.addUserToGroup("kermit", "kermitGroup");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId("optimize");
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermitGroup");
    engineIntegrationExtension.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }


  private void validateUserIsNotAuthorized(String user, String password) {
    Response response = embeddedOptimizeExtension.authenticateUserRequest(user, password);

    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private void validateThatUserCanBeAuthenticated(String user, String password) {
    Response response = embeddedOptimizeExtension.authenticateUserRequest(user, password);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }
}
