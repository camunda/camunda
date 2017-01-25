package org.camunda.optimize.service.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

  @Value("${camunda.optimize.es.correlation.init}")
  private String correlationInitScriptPath;
  @Value("${camunda.optimize.es.correlation.map}")
  private String correlationMapScriptPath;
  @Value("${camunda.optimize.es.correlation.reduce}")
  private String correlationReduceScriptPath;
  @Value("${camunda.optimize.es.index}")
  private String optimizeIndex;
  @Value("${camunda.optimize.es.event.type}")
  private String eventType;
  @Value("${camunda.optimize.es.procdef.type}")
  private String processDefinitionType;

  @Value("${camunda.optimize.engine.rest}")
  private String engineRestApiEndpoint;
  @Value("${camunda.optimize.engine.hai.endpoint}")
  private String historicActivityInstanceEndpoint;
  @Value("${camunda.optimize.engine.procdef.endpoint}")
  private String processDefinitionEndpoint;



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

  public String getCorrelationInitScriptPath() {
    return correlationInitScriptPath;
  }

  public String getCorrelationMapScriptPath() {
    return correlationMapScriptPath;
  }

  public String getCorrelationReduceScriptPath() {
    return correlationReduceScriptPath;
  }

  public String getOptimizeIndex() {
    return optimizeIndex;
  }

  public String getHistoricActivityInstanceEndpoint() {
    return historicActivityInstanceEndpoint;
  }

  public String getEventType() {
    return eventType;
  }

  public String getProcessDefinitionType() {
    return processDefinitionType;
  }

  public String getProcessDefinitionEndpoint() {
    return processDefinitionEndpoint;
  }
}
