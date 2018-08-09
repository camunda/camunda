package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Component
public class EngineAuthenticationProvider {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private ConfigurationService configurationService;

  public boolean authenticate(CredentialsDto credentialsDto, EngineContext engineContext) {
    return performAuthenticationCheck(credentialsDto, engineContext);
  }

  private boolean performAuthenticationCheck(CredentialsDto credentialsDto, EngineContext engineContext) {
      Response response = engineContext.getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()))
        .path(configurationService.getUserValidationEndpoint())
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.json(credentialsDto));

      if (response.getStatus() == 200) {
        AuthenticationResultDto responseEntity = response.readEntity(AuthenticationResultDto.class);
        return responseEntity.isAuthenticated();
      } else {
        logger.error("Could not validate user [{}] against the engine [{}]. " +
            "Maybe you did not provide a user or password or the user is locked",
          credentialsDto.getUsername(),
          engineContext.getEngineAlias()
        );

        // read Exception from the engine response
        // and rethrow it to forward the error message to the client
        // e.g when the user is locked, the error message will contain corresponding information
        throw response.readEntity(RuntimeException.class);
      }
  }
}
