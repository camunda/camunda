package org.camunda.optimize.service.security.impl;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.engine.GroupInfoDto;
import org.camunda.optimize.dto.optimize.query.EngineCredentialsDto;
import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.USER_ID;

/**
 * @author Askar Akhmerov
 */
@Component("engineAuthenticationProvider")
public class EngineAuthenticationProviderImpl implements AuthenticationProvider<EngineCredentialsDto> {
  private static final Logger logger = LoggerFactory.getLogger(EngineAuthenticationProviderImpl.class);

  @Autowired
  private EngineClientFactory engineClientFactory;

  @Autowired
  private ConfigurationService configurationService;

  public boolean authenticate(EngineCredentialsDto credentialsDto) {
    boolean isAuthenticated = performAuthenticationCheck(credentialsDto);
    boolean isAuthorized = true;
    if(configurationService.isAuthorizationCheckNecessary(credentialsDto.getEngineAlias())) {
      isAuthorized = performAuthorizationCheck(credentialsDto);
    }
    return isAuthenticated && isAuthorized;
  }

  private boolean performAuthorizationCheck(EngineCredentialsDto credentialsDto) {
    boolean isAuthorized = false;
    try {
      Response response = getEngineClient(credentialsDto)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(credentialsDto.getEngineAlias()))
          .queryParam(USER_ID, credentialsDto.getUsername())
          .path(configurationService.getGetGroupsEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .get();

      if (response.getStatus() == 200) {
        GroupInfoDto responseEntity = response.readEntity(GroupInfoDto.class);
        isAuthorized = responseEntity.containsGroup(configurationService.getOptimizeAccessGroupId(credentialsDto.getEngineAlias()));
      }

    } catch (Exception e) {
      logger.warn("Connection to engine cannot be established", e.getMessage());
      if (logger.isDebugEnabled()) {
        logger.debug("Engine endpoint: ["
            + configurationService.getEngineRestApiEndpointOfCustomEngine(credentialsDto.getEngineAlias())
            + "] with path ["
            + configurationService.getGetGroupsEndpoint()
            + "]", e);
      }
    }
    return isAuthorized;
  }

  private Client getEngineClient(EngineCredentialsDto credentialsDto) {
    return engineClientFactory.getInstance(credentialsDto.getEngineAlias());
  }

  private boolean performAuthenticationCheck(EngineCredentialsDto credentialsDto) {
    boolean authenticated = false;
    try {
      Response response = getEngineClient(credentialsDto)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(credentialsDto.getEngineAlias()))
          .path(configurationService.getUserValidationEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .post(Entity.json(credentialsDto));

      if (response.getStatus() == 200) {
        AuthenticationResultDto responseEntity = response.readEntity(AuthenticationResultDto.class);
        authenticated = responseEntity.isAuthenticated();
      }

    } catch (Exception e) {
      logger.warn("Connection to engine cannot be established", e.getMessage());
      if (logger.isDebugEnabled()) {
        logger.debug("Engine endpoint: ["
            + configurationService.getEngineRestApiEndpointOfCustomEngine(credentialsDto.getEngineAlias())
            + "] with path ["
            + configurationService.getUserValidationEndpoint()
            + "]", e);
      }
    }

    return authenticated;
  }

}
