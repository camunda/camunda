package org.camunda.optimize.rest;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class AuthenticationRestServiceEngineIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
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
    engineRule.addUser("kermit", "kermit");


    //when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    //then
    assertThat(response.getStatus(),is(200));
  }

  @Test
  public void everyUserCanBeAuthenticatedFromEngineIfNoAccessGroupWasSpecified() {
    // given
    setAccessGroupInConfiguration("");
    engineRule.addUser("kermit", "kermit");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

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
    engineRule.addUser("kermit", "kermit");
    engineRule.addUserToGroup("kermit", "optimizeGroup");
    engineRule.addUser("genzo", "frog");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    // then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));

    // when
    response = embeddedOptimizeRule.authenticateUserRequest("genzo", "frog");

    // then
    assertThat(response.getStatus(),is(401));
  }

  private void setAccessGroupInConfiguration(String s) {
    configurationService.getConfiguredEngines().get("1").getAuthentication().setAccessGroup(s);
  }

}