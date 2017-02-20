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
  @Value("${camunda.optimize.auth.default.user}")
  private String defaultUser;
  @Value("${camunda.optimize.auth.default.password}")
  private String defaultPassword;
  @Value("${camunda.optimize.engine.auth.enabled}")
  private boolean engineAuthenticationEnabled;

  @Value("${camunda.optimize.serialization.date.format}")
  private String dateFormat;
  @Value("${camunda.optimize.engine.import.page.size.max}")
  private int engineImportMaxPageSize;
  @Value("${camunda.optimize.engine.import.jobqueue.size.max}")
  private int maxJobQueueSize;
  @Value("${camunda.optimize.engine.import.executor.thread.count}")
  private int importExecutorThreadCount;

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
  @Value("${camunda.optimize.es.procdef.xml.type}")
  private String processDefinitionXmlType;
  @Value("${camunda.optimize.es.users.type}")
  private String elasticSearchUsersType;

  @Value("${camunda.optimize.engine.rest}")
  private String engineRestApiEndpoint;
  @Value("${camunda.optimize.engine.hai.endpoint}")
  private String historicActivityInstanceEndpoint;
  @Value("${camunda.optimize.engine.hai.count.endpoint}")
  private String historicActivityInstanceCountEndpoint;
  @Value("${camunda.optimize.engine.procdef.endpoint}")
  private String processDefinitionEndpoint;
  @Value("${camunda.optimize.engine.procdef.count.endpoint}")
  private String processDefinitionCountEndpoint;
  @Value("${camunda.optimize.engine.procdef.xml.endpoint}")
  private String processDefinitionXmlEndpoint;
  @Value("${camunda.optimize.engine.user.validation.endpoint}")
  private String userValidationEndpoint;
  @Value("${camunda.optimize.engine.name}")
  private String engineName;


  @Value("${camunda.optimize.es.analyzer.name}")
  private String analyzerName;
  @Value("${camunda.optimize.es.analyzer.tokenizer}")
  private String tokenizer;
  @Value("${camunda.optimize.es.analyzer.tokenfilter}")
  private String tokenFilter;
  @Value("${camunda.optimize.es.import.handler.interval}")
  private long importHandlerWait;
  @Value("${camunda.optimize.es.import.handler.max.backoff}")
  private long maximumBackoff;


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

  public String getEngineRestApiEndpointOfCustomEngine() {
    return engineRestApiEndpoint + engineName;
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

  public String getHistoricActivityInstanceCountEndpoint() {
    return historicActivityInstanceCountEndpoint;
  }

  public String getEventType() {
    return eventType;
  }

  public String getUserValidationEndpoint() {
    return userValidationEndpoint;
  }

  public String getEngineName() {
    return engineName;
  }

  public String getProcessDefinitionType() {
    return processDefinitionType;
  }

  public String getProcessDefinitionEndpoint() {
    return processDefinitionEndpoint;
  }

  public String getProcessDefinitionCountEndpoint() {
    return processDefinitionCountEndpoint;
  }

  public String getProcessDefinitionXmlType() {
    return processDefinitionXmlType;
  }

  public String getProcessDefinitionXmlEndpoint(String processDefinitionId) {
    String processDefinitionXmlEndpoint =
      processDefinitionEndpoint + "/" + processDefinitionId + this.processDefinitionXmlEndpoint;
    return processDefinitionXmlEndpoint;
  }

  public String getElasticSearchUsersType() {
    return elasticSearchUsersType;
  }

  public String getAnalyzerName() {
    return analyzerName;
  }

  public String getTokenizer() {
    return tokenizer;
  }

  public String getTokenFilter() {
    return tokenFilter;
  }

  public boolean isEngineAuthenticationEnabled() {
    return engineAuthenticationEnabled;
  }

  public String getDefaultUser() {
    return defaultUser;
  }

  public String getDefaultPassword() {
    return defaultPassword;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public int getEngineImportMaxPageSize() {
    return engineImportMaxPageSize;
  }

  public long getImportHandlerWait() {
    return importHandlerWait;
  }

  public long getMaximumBackoff() {
    return maximumBackoff;
  }

  public int getMaxJobQueueSize() {
    return maxJobQueueSize;
  }

  public int getImportExecutorThreadCount() {
    return importExecutorThreadCount;
  }
}
