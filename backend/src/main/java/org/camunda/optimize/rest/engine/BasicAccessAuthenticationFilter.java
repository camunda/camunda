package org.camunda.optimize.rest.engine;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BasicAccessAuthenticationFilter implements ClientRequestFilter {
  protected String engineAlias;
  protected ConfigurationService configurationService;

  public BasicAccessAuthenticationFilter(String engineAlias, ConfigurationService configurationService) {
    this.engineAlias = engineAlias;
    this.configurationService = configurationService;
  }

  public void filter(ClientRequestContext requestContext) throws IOException {
    MultivaluedMap<String, Object> headers = requestContext.getHeaders();
    final String basicAuthentication = getBasicAuthentication(engineAlias);
    headers.add("Authorization", basicAuthentication);

  }

  private String getBasicAuthentication(String engineAlias) {
    String token =
      configurationService.getDefaultEngineAuthenticationUser(engineAlias) +
      ":" +
      configurationService.getDefaultEngineAuthenticationPassword(engineAlias);
    return "Basic " + DatatypeConverter.printBase64Binary(token.getBytes(StandardCharsets.UTF_8));
  }
}

