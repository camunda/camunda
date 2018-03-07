package org.camunda.optimize.rest;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.engine.CredentialsDto;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import java.io.UnsupportedEncodingException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class AuthenticationRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void disableDefaultUserCreation() {
    // given
    ConfigurationService configurationService = embeddedOptimizeRule.getConfigurationService();
    configurationService.setDefaultUserCreationEnabled(false);
    embeddedOptimizeRule.reloadConfiguration();

    // when
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(configurationService.getDefaultUser());
    entity.setPassword(configurationService.getDefaultPassword());

    Response response = embeddedOptimizeRule.target("authentication")
      .request()
      .post(Entity.json(entity));

    // then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void authenticateUserUsingES() throws Exception {
    // given
    elasticSearchRule.addDemoUser();

    //when
    Response response = embeddedOptimizeRule.authenticateDemoRequest();

    //then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));
  }

  @Test
  public void logout() throws Exception {
    //given
    elasticSearchRule.addDemoUser();
    String token = embeddedOptimizeRule.authenticateDemo();

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
  public void securingRestApiWorksWithProxy() throws Exception {
    //given
    elasticSearchRule.addDemoUser();
    String token = embeddedOptimizeRule.authenticateDemo();

    //when
    Response testResponse = embeddedOptimizeRule.target("authentication/test")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1v")
        .header(AuthenticationUtil.OPTIMIZE_AUTHORIZATION,"Bearer " + token)
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
  public void cantKickOutUserByProvidingWrongToken() throws JsonProcessingException, UnsupportedEncodingException {
    // given
    elasticSearchRule.addDemoUser();
    embeddedOptimizeRule.authenticateDemo();
    Algorithm algorithm = Algorithm.HMAC256("secret");
    String selfGeneratedEvilToken = JWT.create()
        .withIssuer("demo")
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
  public void authenticatingSameUserTwiceDisablesFirstToken() throws JsonProcessingException {
    // given
    elasticSearchRule.addDemoUser();
    String firstToken = embeddedOptimizeRule.authenticateDemo();
    embeddedOptimizeRule.authenticateDemo();

    // when
    Response logoutResponse = embeddedOptimizeRule.target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + firstToken)
        .get();

    // then
    assertThat(logoutResponse.getStatus(), is(401));
  }

}