package org.camunda.optimize.service.security.impl;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.engine.CredentialsDto;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceDto;
import org.camunda.optimize.service.exceptions.UnauthorizedUserException;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component ("engineAuthenticationProvider")
public class EngineAuthenticationProviderImpl implements AuthenticationProvider {

  @Autowired
  private Client client;

  @Autowired
  private ConfigurationService configurationService;

  public boolean authenticate(String username, String password) {
    boolean authenticated;
    CredentialsDto credentials = new CredentialsDto();
    credentials.setUsername(username);
    credentials.setPassword(username);
    Response response =client
        .target(configurationService.getEngineRestApiEndpoint())
        .path(configurationService.getUserValidationEndpoint())
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.json(credentials));
    if (response.getStatus() != 200) {
      authenticated = false;
    } else {
      AuthenticationResultDto responseEntity = response.readEntity(AuthenticationResultDto.class);
      authenticated = responseEntity.isAuthenticated();
    }

    return authenticated;
  }

}
