package org.camunda.optimize.rest.engine;

import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Component
public class BasicAccessAuthenticationFilter implements ClientRequestFilter {

  private Logger logger = LoggerFactory.getLogger(BasicAccessAuthenticationFilter.class);

  @Autowired
  private ConfigurationService configurationService;

  public void filter(ClientRequestContext requestContext) throws IOException {
    MultivaluedMap<String, Object> headers = requestContext.getHeaders();
    final String basicAuthentication = getBasicAuthentication();
    headers.add("Authorization", basicAuthentication);

  }

  private String getBasicAuthentication() {
    String token = configurationService.getDefaultUser() + ":" + configurationService.getDefaultPassword();
    try {
      return "Basic " + DatatypeConverter.printBase64Binary(token.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException ex) {
      logger.error("Cannot encode with UTF-8", new IllegalStateException("Cannot encode with UTF-8", ex));
    }
    return null;
  }
}

