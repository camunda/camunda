package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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

//  @Test
//  public void authenticateLockedUser() {
//    //given
//    engineRule.addUser("kermit", "kermit");
//    engineRule.grantUserOptimizeAccess("kermit");
//
//    //when
//    embeddedOptimizeRule.authenticateUserRequest("kermit", "wrongPassword");
//    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");
//
//    //then
//    assertThat(response.getStatus(),is(500));
//  }

  @Test
  public void authenticateUnknownUser() {
    //when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(),is(401));
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
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");
    assertThat(response.getStatus(),is(403));
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

  private String authenticateAdminUser() {
    return embeddedOptimizeRule.authenticateUser("admin","admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineRule.addUser("admin", "admin");
    engineRule.grantUserOptimizeAccess("admin");
  }
}
