package org.camunda.optimize.service.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Link;

/**
 * @author Askar Akhmerov
 */
@Component
public class ConfigurationService {

  @Value("${camunda.optimize.auth.token.live.min}")
  private Integer lifetime;
  @Value("${camunda.optimize.auth.token.secret}")
  private String secret;

  @Value("${camunda.optimize.es.host}")
  private String elasticSearchHost;
  @Value("${camunda.optimize.es.port}")
  private Integer elasticSearchPort;

  @Value("${camunda.optimize.engine.rest}")
  private String engineRestApiEndpoint;

  public String getSecret() {
    return secret;
  }

  public Integer getLifetime() {
    return lifetime;
  }

  public String getElasticSearchHost() {
    return elasticSearchHost;
  }

  public Integer getElasticSearchPort() {
    return elasticSearchPort;
  }

  public String getEngineRestApiEndpoint() {
    return engineRestApiEndpoint;
  }
}
