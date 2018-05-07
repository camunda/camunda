package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

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
import static org.junit.Assert.assertThat;


public class ApplicationAuthorizationServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void grantAccessOnGlobally() {
    // given
    engineRule.addUser("genzo", "genzo");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("genzo", "genzo");
  }

  @Test
  public void grantAccessForGroupForAllResources() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.createGroup("kermit-group", "foo", "foo");
    engineRule.addUserToGroup("kermit", "kermit-group");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermit-group");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForGroupForOptimizeResource() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.createGroup("kermit-group", "foo", "foo");
    engineRule.addUserToGroup("kermit", "kermit-group");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermit-group");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForUserForAllResources() {
    // given
    engineRule.addUser("kermit", "kermit");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessForUserForOptimizeResource() {
    // given
    engineRule.addUser("kermit", "kermit");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void revokeAccessForGroupForAllResources() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.createGroup("kermit-group", "foo", "foo");
    engineRule.addUserToGroup("kermit", "kermit-group");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineRule.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermit-group");
    authorizationDto.setUserId(null);
    engineRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForGroupForOptimizeResource() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.createGroup("kermit-group", "foo", "foo");
    engineRule.addUserToGroup("kermit", "kermit-group");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineRule.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setGroupId("kermit-group");
    authorizationDto.setUserId(null);
    engineRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForUserForAllResources() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.createGroup("kermit-group", "foo", "foo");
    engineRule.addUserToGroup("kermit", "kermit-group");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermit-group");
    engineRule.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void revokeAccessForUserForOptimizeResource() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.createGroup("kermit-group", "foo", "foo");
    engineRule.addUserToGroup("kermit", "kermit-group");
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermit-group");
    engineRule.createAuthorization(authorizationDto);
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }

  @Test
  public void grantRevokeGrantRevokeAccess() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.createGroup("kermit-group", "foo", "foo");
    engineRule.addUserToGroup("kermit", "kermit-group");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermit-group");
    authorizationDto.setUserId(null);
    engineRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");

    // when
    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermit-group");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");

    // when
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setUserId("kermit");
    authorizationDto.setGroupId(null);
    engineRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");

    // when
    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("kermit");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateThatUserCanBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void grantAccessIsSpecificToUsers() {
    // given
    engineRule.addUser("genzo", "genzo");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId("admin");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("genzo", "genzo");
  }

  @Test
  public void addGroupAccessAndRevokeAccessForGroupForOptimizeResource() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.createGroup("kermit-group", "foo", "foo");
    engineRule.addUserToGroup("kermit", "kermit-group");

    // when
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId("kermit-group");
    engineRule.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId("optimize");
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId("kermit-group");
    engineRule.createAuthorization(authorizationDto);

    // then
    validateUserIsNotAuthorized("kermit", "kermit");
  }


  private void validateUserIsNotAuthorized(String user, String password) {
    Response response = embeddedOptimizeRule.authenticateUserRequest(user, password);

    assertThat(response.getStatus(),is(403));
  }
  private void validateThatUserCanBeAuthenticated(String user, String password) {
    Response response = embeddedOptimizeRule.authenticateUserRequest(user, password);

    assertThat(response.getStatus(),is(200));
  }
}
