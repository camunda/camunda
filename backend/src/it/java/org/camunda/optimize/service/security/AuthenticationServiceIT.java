package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.time.OffsetDateTime;

import static org.camunda.optimize.rest.util.AuthenticationUtil.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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
    assertThat(response.getStatus(), is(200));
  }

  //comment in back after stopping support of engine 7.8
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
//    assertThat(response.getStatus(),is(401));
//    String errorMessage = response.readEntity(String.class);
//    assertThat(errorMessage, containsString("The user with id 'kermit' is locked."));
//  }

  @Test
  public void authenticateUnknownUser() {
    //when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void securingRestApiWorksWithProxy() {
    //given
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    //when
    Response testResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildAuthTestRequest()
        .withoutAuthentication()
        .addSingleCookie(OPTIMIZE_AUTHORIZATION, "Bearer " + token)
        .addSingleCookie(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1v")
        .execute();

    //then
    assertThat(testResponse.getStatus(), is(200));
    String responseEntity = testResponse.readEntity(String.class);
    assertThat(responseEntity, is("OK"));
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
    Response logoutResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthCookie("Bearer " + selfGeneratedEvilToken)
        .execute();

    //then
    assertThat(logoutResponse.getStatus(), is(401));
  }

  @Test
  public void authenticatingSameUserTwiceCreatesNewIndependentSession() {
    // given
    addAdminUserAndGrantAccessPermission();

    final String firstToken = authenticateAdminUser();
    final String secondToken = authenticateAdminUser();

    // when
    final Response logoutResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildLogOutRequest()
        .withGivenAuthCookie("Bearer " + firstToken)
        .execute();
    assertThat(logoutResponse.getStatus(), is(200));

    // then
    final Response getReportsResponse =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetAllReportsRequest()
        .withGivenAuthCookie("Bearer " + secondToken)
        .execute();

    assertThat(getReportsResponse.getStatus(), is(200));
  }

  @Test
  public void cantAuthenticateWithoutAnyAuthorization() {
    // when
    engineRule.addUser("kermit", "kermit");

    // then
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void tokenShouldExpireAfterConfiguredTime() {
    // given
    int expiryTime = embeddedOptimizeRule.getConfigurationService().getTokenLifeTimeMinutes();
    engineRule.addUser("genzo", "genzo");
    engineRule.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeRule.authenticateUser("genzo", "genzo");

    Response testAuthenticationResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthCookie("Bearer " + firstToken)
      .execute();
    assertThat(testAuthenticationResponse.getStatus(), is(200));

    // when
    LocalDateUtil.setCurrentTime(get1MinuteAfterExpiryTime(expiryTime));
    testAuthenticationResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthCookie("Bearer " + firstToken)
      .execute();

    //then
    assertThat(testAuthenticationResponse.getStatus(), is(401));
  }

  @Test
  public void authCookieIsExtendedByRequestInLastThirdOfLifeTime() {
    // given
    int expiryMinutes = embeddedOptimizeRule.getConfigurationService().getTokenLifeTimeMinutes();
    engineRule.addUser("genzo", "genzo");
    engineRule.grantUserOptimizeAccess("genzo");
    String firstToken = embeddedOptimizeRule.authenticateUser("genzo", "genzo");

    Response testAuthenticationResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthCookie("Bearer " + firstToken)
      .execute();
    assertThat(testAuthenticationResponse.getStatus(), is(200));

    // when
    final OffsetDateTime dateTimeBeforeRefresh = LocalDateUtil.getCurrentDateTime();
    LocalDateUtil.setCurrentTime(LocalDateUtil.getCurrentDateTime().plusMinutes(expiryMinutes * 2 / 3));
    testAuthenticationResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAuthTestRequest()
      .withGivenAuthCookie("Bearer " + firstToken)
      .execute();

    //then
    assertThat(testAuthenticationResponse.getStatus(), is(200));
    assertThat(testAuthenticationResponse.getCookies().keySet(), hasItem(OPTIMIZE_AUTHORIZATION));
    final NewCookie newAuthCookie = testAuthenticationResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    assertThat(newAuthCookie.getValue(), is(not(equalTo(firstToken))));
    assertThat(
      newAuthCookie.getExpiry().toInstant(),
      is(greaterThan(dateTimeBeforeRefresh.plusMinutes(expiryMinutes).toInstant()))
    );

  }

  private OffsetDateTime get1MinuteAfterExpiryTime(int expiryTime) {
    return LocalDateUtil.getCurrentDateTime().plusMinutes(expiryTime + 1);
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeRule.authenticateUser("admin", "admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineRule.addUser("admin", "admin");
    engineRule.grantUserOptimizeAccess("admin");
  }
}
