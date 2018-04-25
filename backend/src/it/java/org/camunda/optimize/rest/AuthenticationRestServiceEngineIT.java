package org.camunda.optimize.rest;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class AuthenticationRestServiceEngineIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticsearchIntegrationRule elasticSearchRule = new ElasticsearchIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Before
  public void init() {
    configurationService = embeddedOptimizeRule.getConfigurationService();
  }

  @After
  public void reset() {
    setAccessGroupInConfiguration("");
  }

  @Test
  public void authenticateUser() throws Exception {
    //given
    engineRule.addUser("demo", "demo");


    //when
    Response response = embeddedOptimizeRule.authenticateUserRequest("demo", "demo");

    //then
    assertThat(response.getStatus(),is(200));
  }

  @Test
  public void everyUserCanBeAuthenticatedFromEngineIfNoAccessGroupWasSpecified() {
    // given
    setAccessGroupInConfiguration("");
    engineRule.addUser("demo", "demo");

    // when
    Response response = embeddedOptimizeRule.authenticateDemoRequest();

    //then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));
  }

  @Test
  public void onlyUsersAddedToAccessGroupCanBeAuthenticatedFromEngine() {
    // given
    setAccessGroupInConfiguration("optimizeGroup");
    engineRule.createGroup("optimizeGroup", "Optimize Access Group", "Foo type");
    engineRule.addUser("demo", "demo");
    engineRule.addUserToGroup("demo", "optimizeGroup");
    engineRule.addUser("kermit", "frog");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("demo", "demo");

    // then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));

    // when
    response = embeddedOptimizeRule.authenticateUserRequest("kermit", "frog");

    // then
    assertThat(response.getStatus(),is(401));
  }

  private void setAccessGroupInConfiguration(String s) {
    configurationService.getConfiguredEngines().get("1").getAuthentication().setAccessGroup(s);
  }

}