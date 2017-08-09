package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class AuthenticationRestServiceEngineIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

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
    ConfigurationService configurationService = embeddedOptimizeRule.getConfigurationService();
    configurationService.setOptimizeAccessGroupId("");
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
    ConfigurationService configurationService = embeddedOptimizeRule.getConfigurationService();
    configurationService.setOptimizeAccessGroupId("optimizeGroup");
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

}