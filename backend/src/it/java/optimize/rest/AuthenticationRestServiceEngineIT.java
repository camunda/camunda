package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
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
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class AuthenticationRestServiceEngineIT {
  private static final String USERNAME_PASSWORD = "demo";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void authenticateUser() throws Exception {
    //given
    UserDto userDto = constructDemoUserDto();

    Response res = engineRule.target()
        .path("/user/create")
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.json(userDto));
    assertThat(res.getStatus(),is(204));

    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(USERNAME_PASSWORD);
    entity.setPassword(USERNAME_PASSWORD);

    //when
    Response response = embeddedOptimizeRule.target("authentication")
        .request()
        .post(Entity.json(entity));

    //then
    assertThat(response.getStatus(),is(200));
  }

  private UserDto constructDemoUserDto() {
    UserProfileDto profile = new UserProfileDto();
    profile.setEmail("demo@camunda.org");
    profile.setId(USERNAME_PASSWORD);
    UserCredentialsDto credentials = new UserCredentialsDto();
    credentials.setPassword(USERNAME_PASSWORD);
    UserDto userDto = new UserDto();
    userDto.setProfile(profile);
    userDto.setCredentials(credentials);
    return userDto;
  }

}