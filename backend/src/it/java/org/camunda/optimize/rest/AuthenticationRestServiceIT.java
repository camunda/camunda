package org.camunda.optimize.rest;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class AuthenticationRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void authenticateUserUsingES() {
    // given
    addAdminUser();

    //when
    Response response = embeddedOptimizeRule.authenticateUserRequest("admin", "admin");

    //then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));
  }

  @Test
  public void logout() {
    //given
    engineIntegrationRule.addUser("admin", "admin");
    String token = authenticateAdminUser();

    //when
    Response logoutResponse = embeddedOptimizeRule.target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get();

    //then
    assertThat(logoutResponse.getStatus(),is(200));
    String responseEntity = logoutResponse.readEntity(String.class);
    assertThat(responseEntity,is("OK"));
  }

  @Test
  public void securingRestApiWorksWithProxy() {
    //given
    addAdminUser();
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
  public void logoutSecure() {

    //when
    Response logoutResponse = embeddedOptimizeRule.target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + "randomToken")
        .get();

    //then
    assertThat(logoutResponse.getStatus(),is(401));
  }

  @Test
  public void cantKickOutUserByProvidingWrongToken() throws UnsupportedEncodingException {
    // given
    addAdminUser();
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
    addAdminUser();
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

  private String authenticateAdminUser() {
    return embeddedOptimizeRule.authenticateUser("admin","admin");
  }

  private void addAdminUser() {
    engineIntegrationRule.addUser("admin", "admin");
  }

}