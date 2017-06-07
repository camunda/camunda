package org.camunda.optimize.rest;

import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class AuthenticationRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);


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
        .header(AuthenticationUtil.OPTIMIZE_AUTHORIZATION_HEADER,"Bearer " + token)
        .get();

    //then
    assertThat(testResponse.getStatus(),is(200));
    String responseEntity = testResponse.readEntity(String.class);
    assertThat(responseEntity,is("OK"));
  }

  @Test
  public void logoutSecure() throws Exception {

    //when
    Response logoutResponse = embeddedOptimizeRule.target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + "randomToken")
        .get();

    //then
    assertThat(logoutResponse.getStatus(),is(401));
  }

}