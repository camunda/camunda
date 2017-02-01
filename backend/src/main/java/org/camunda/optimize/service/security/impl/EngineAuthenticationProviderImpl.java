package org.camunda.optimize.service.security.impl;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.util.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Askar Akhmerov
 */
@Component ("engineAuthenticationProvider")
public class EngineAuthenticationProviderImpl implements AuthenticationProvider {

  @Autowired
  private Client client;

  @Autowired
  private ConfigurationService configurationService;

  public boolean authenticate(CredentialsDto credentialsDto) {
    boolean authenticated;
    Response response =client
        .target(configurationService.getEngineRestApiEndpoint())
        .path(configurationService.getUserValidationEndpoint())
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.json(credentialsDto));
    if (response.getStatus() != 200) {
      authenticated = false;
    } else {
      AuthenticationResultDto responseEntity = response.readEntity(AuthenticationResultDto.class);
      authenticated = responseEntity.isAuthenticated();
    }

    return authenticated;
  }

}
