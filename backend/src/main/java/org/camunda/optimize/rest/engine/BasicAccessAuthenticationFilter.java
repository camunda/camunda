package org.camunda.optimize.rest.engine;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BasicAccessAuthenticationFilter implements ClientRequestFilter {
  private String defaultEngineAuthenticationUser;
  private String defaultEngineAuthenticationPassword;

  public BasicAccessAuthenticationFilter(String defaultEngineAuthenticationUser, String defaultEngineAuthenticationPassword) {
    this.defaultEngineAuthenticationUser = defaultEngineAuthenticationUser;
    this.defaultEngineAuthenticationPassword = defaultEngineAuthenticationPassword;
  }

  public void filter(ClientRequestContext requestContext) throws IOException {
    MultivaluedMap<String, Object> headers = requestContext.getHeaders();
    final String basicAuthentication = getBasicAuthentication();
    headers.add("Authorization", basicAuthentication);

  }

  private String getBasicAuthentication() {
    String token = defaultEngineAuthenticationUser + ":" + defaultEngineAuthenticationPassword;
    return "Basic " + DatatypeConverter.printBase64Binary(token.getBytes(StandardCharsets.UTF_8));
  }
}

