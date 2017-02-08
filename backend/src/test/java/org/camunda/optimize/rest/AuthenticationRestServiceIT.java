package org.camunda.optimize.rest;

import org.camunda.optimize.rest.engine.dto.UserDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class AuthenticationRestServiceIT extends AbstractJerseyTest {
  private static final String USERNAME_PASSWORD = "demo";

  @Autowired
  @Rule
  public EngineIntegrationRule rule;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client engineClient;

  @Test
  public void authenticateUser() throws Exception {
    //given
    UserDto userDto = constructDemoUserDto();

    Response res = engineClient.target(configurationService.getEngineRestApiEndpoint())
        .path("/user/create")
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.json(userDto));
    assertThat(res.getStatus(),is(204));

    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(USERNAME_PASSWORD);
    entity.setPassword(USERNAME_PASSWORD);

    //when
    Response response = target("authentication")
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

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }
}