package org.camunda.optimize.service.security.impl;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.engine.GroupInfoDto;
import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.USER_ID;

@Component("engineAuthenticationProvider")
public class EngineAuthenticationProviderImpl implements AuthenticationProvider<CredentialsDto> {
  private static final Logger logger = LoggerFactory.getLogger(EngineAuthenticationProviderImpl.class);

  @Autowired
  private EngineContextFactory engineContextFactory;

  @Autowired
  private ConfigurationService configurationService;

  public boolean authenticate(CredentialsDto credentialsDto) {
    boolean result = false;
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      boolean isAuthenticated = performAuthenticationCheck(credentialsDto, engineContext);
      boolean isAuthorized = true;
      if (configurationService.isAuthorizationCheckNecessary(engineContext.getEngineAlias())) {
        isAuthorized = performAuthorizationCheck(credentialsDto, engineContext);
      }
      if (isAuthenticated && isAuthorized) {
        result = true;
        break;
      }
    }
    return result;
  }

  private boolean performAuthorizationCheck(CredentialsDto credentialsDto, EngineContext engineContext) {
    boolean isAuthorized = false;

    try {
      Response response = engineContext.getEngineClient()
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()))
          .queryParam(USER_ID, credentialsDto.getId())
          .path(configurationService.getGetGroupsEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .get();

      if (response.getStatus() == 200) {
        GroupInfoDto responseEntity = response.readEntity(GroupInfoDto.class);
        isAuthorized = responseEntity.containsGroup(configurationService.getOptimizeAccessGroupId(engineContext.getEngineAlias()));
      }

    } catch (Exception e) {
      logger.warn("Connection to engine cannot be established", e.getMessage());
      if (logger.isDebugEnabled()) {
        logger.debug("Engine endpoint: ["
            + configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias())
            + "] with path ["
            + configurationService.getGetGroupsEndpoint()
            + "]", e);
      }
    }

    return isAuthorized;
  }


  private boolean performAuthenticationCheck(CredentialsDto credentialsDto, EngineContext engineContext) {
    boolean authenticated = false;
    try {
      org.camunda.optimize.dto.engine.CredentialsDto engineCredentials =
        new org.camunda.optimize.dto.engine.CredentialsDto();
      engineCredentials.setPassword(credentialsDto.getPassword());
      engineCredentials.setUsername(credentialsDto.getId());
      Response response = engineContext.getEngineClient()
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()))
          .path(configurationService.getUserValidationEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .post(Entity.json(engineCredentials));

      if (response.getStatus() == 200) {
        AuthenticationResultDto responseEntity = response.readEntity(AuthenticationResultDto.class);
        authenticated = responseEntity.isAuthenticated();
      }

    } catch (Exception e) {
      logger.warn("Connection to engine cannot be established", e.getMessage());
      if (logger.isDebugEnabled()) {
        logger.debug("Engine endpoint: ["
            + configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias())
            + "] with path ["
            + configurationService.getUserValidationEndpoint()
            + "]", e);
      }
    }

    return authenticated;
  }

}
