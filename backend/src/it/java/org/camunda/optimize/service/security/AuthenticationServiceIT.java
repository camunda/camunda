package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.time.OffsetDateTime;
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


public class AuthenticationServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void authenticateUser() {
    //given
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");

    //when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(),is(200));
  }

  @Test
  public void securingRestApiWorksWithProxy() {
    //given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    //when
    Response testResponse = embeddedOptimizeRule.target("authentication/test")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1v")
        .header(AuthenticationUtil.OPTIMIZE_AUTHORIZATION, "Bearer " + token)
        .get();

    //then
    assertThat(testResponse.getStatus(),is(200));
    String responseEntity = testResponse.readEntity(String.class);
    assertThat(responseEntity,is("OK"));
  }

  @Test
  public void cantKickOutUserByProvidingWrongToken() throws UnsupportedEncodingException {
    // given
    addAdminUserAndGrantAccessPermission();
    authenticateAdminUser();
    Algorithm algorithm = Algorithm.HMAC256("secret");
    String selfGeneratedEvilToken = JWT.create()
        .withIssuer("admin")
        .sign(algorithm);

    //when
    Response logoutResponse = embeddedOptimizeRule.target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + selfGeneratedEvilToken)
        .get();

    //then
    assertThat(logoutResponse.getStatus(), is(401));
  }

  @Test
  public void authenticatingSameUserTwiceDisablesFirstToken() {
    // given
    addAdminUserAndGrantAccessPermission();
    String firstToken = authenticateAdminUser();
    authenticateAdminUser();

    // when
    Response logoutResponse = embeddedOptimizeRule.target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + firstToken)
        .get();

    // then
    assertThat(logoutResponse.getStatus(), is(401));
  }

  @Test
  public void cantAuthenticateWithoutAnyAuthorization() {
    // when
    engineRule.addUser("kermit", "kermit");

    // then
    validateUserCannotBeAuthenticated("kermit", "kermit");
  }

  @Test
  public void tokenShouldExpireAfterConfiguredTime() {
    // given
    int expiryTime = embeddedOptimizeRule.getConfigurationService().getTokenLifeTime();
    engineRule.addUser("genzo", "genzo");
    engineRule.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeRule.authenticateUser("genzo", "genzo");

    // when
    Response testAuthenticationResponse = embeddedOptimizeRule.target("authentication/test")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
        .get();

    //then
    assertThat(testAuthenticationResponse.getStatus(),is(200));

    // when
    LocalDateUtil.setCurrentTime(get1MinuteAfterExpiryTime(expiryTime));
    testAuthenticationResponse = embeddedOptimizeRule.target("authentication/test")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
        .get();

    //then
    assertThat(testAuthenticationResponse.getStatus(),is(401));

  }

  private OffsetDateTime get1MinuteAfterExpiryTime(int expiryTime) {
    return LocalDateUtil.getCurrentDateTime().plusMinutes(expiryTime+1);
  }

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
    validateUserCannotBeAuthenticated("kermit", "kermit");
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
    validateUserCannotBeAuthenticated("kermit", "kermit");
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
    validateUserCannotBeAuthenticated("kermit", "kermit");
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
    validateUserCannotBeAuthenticated("kermit", "kermit");
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
    validateUserCannotBeAuthenticated("kermit", "kermit");

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
    validateUserCannotBeAuthenticated("kermit", "kermit");

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
    validateUserCannotBeAuthenticated("genzo", "genzo");
  }


  private void validateUserCannotBeAuthenticated(String user, String password) {
    Response response = embeddedOptimizeRule.authenticateUserRequest(user, password);

    assertThat(response.getStatus(),is(401));
  }
  private void validateThatUserCanBeAuthenticated(String user, String password) {
    Response response = embeddedOptimizeRule.authenticateUserRequest(user, password);

    assertThat(response.getStatus(),is(200));
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeRule.authenticateUser("admin","admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineRule.addUser("admin", "admin");
    engineRule.grantUserOptimizeAccess("admin");
  }
}
