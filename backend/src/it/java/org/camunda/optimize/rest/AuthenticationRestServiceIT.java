package org.camunda.optimize.rest;

import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class AuthenticationRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void authenticateUser() {
    // given
    addAdminUserAndGrantAccessPermission();

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
    addAdminUserAndGrantAccessPermission();
    String token = authenticateAdminUser();

    //when
    Response logoutResponse = embeddedOptimizeRule
            .getRequestExecutor()
            .buildLogOutRequest()
            .withGivenAuthToken(token)
            .execute();

    //then
    assertThat(logoutResponse.getStatus(),is(200));
    String responseEntity = logoutResponse.readEntity(String.class);
    assertThat(responseEntity,is("OK"));
  }

  @Test
  public void logoutSecure() {

    //when
    Response logoutResponse = embeddedOptimizeRule
            .getRequestExecutor()
            .buildLogOutRequest()
            .withGivenAuthToken("randomToken")
            .execute();

    //then
    assertThat(logoutResponse.getStatus(),is(401));
  }

  @Test
  public void testAuthenticationIfNotAuthenticated() {
    //when
    Response logoutResponse = embeddedOptimizeRule
            .getRequestExecutor()
            .buildAuthTestRequest()
            .withoutAuthentication()
            .execute();

    //then
    assertThat(logoutResponse.getStatus(),is(401));
  }

  @Test
  public void testIfAuthenticated() {
    //when
    Response logoutResponse = embeddedOptimizeRule
            .getRequestExecutor()
            .buildAuthTestRequest()
            .execute();

    //then
    assertThat(logoutResponse.getStatus(),is(200));
  }

  private String authenticateAdminUser() {
    return embeddedOptimizeRule.authenticateUser("admin","admin");
  }

  private void addAdminUserAndGrantAccessPermission() {
    engineIntegrationRule.addUser("admin", "admin");
    engineIntegrationRule.grantUserOptimizeAccess("admin");
  }

}