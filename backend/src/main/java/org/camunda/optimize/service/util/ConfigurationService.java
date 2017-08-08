package org.camunda.optimize.service.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.util.StringUtil.splitStringByComma;
import static org.camunda.optimize.service.util.ValidationHelper.ensureGreaterThanZero;

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
  @Value(("${camunda.optimize.auth.default.user.creation.enabled}"))
  private boolean defaultUserCreationEnabled;
  @Value("${camunda.optimize.engine.auth.user}")
  private String defaultEngineAuthenticationUser;
  @Value("${camunda.optimize.engine.auth.password}")
  private String defaultEngineAuthenticationPassword;
  @Value("${camunda.optimize.engine.auth.enabled}")
  private boolean engineAuthenticationEnabled;

  // optimize
  @Value("${camunda.optimize.serialization.date.format}")
  private String dateFormat;
  @Value("${camunda.optimize.engine.import.jobqueue.size.max}")
  private int maxJobQueueSize;
  @Value("${camunda.optimize.engine.import.executor.thread.count}")
  private int importExecutorThreadCount;
  @Value("${camunda.optimize.engine.import.writer.number.of.retries}")
  private int numberOfRetriesOnConflict;
  @Value("${camunda.optimize.engine.import.process-definition-list}")
  private String processDefinitionsToImport;
  @Value(("${camunda.optimize.variable.max.valueList.size}"))
  private int maxVariableValueListSize;

  // pagination
  @Value("${camunda.optimize.engine.import.page.size.max}")
  private int engineImportMaxPageSize;
  @Value("${camunda.optimize.engine.import.process-definition.page.size.max}")
  private int engineImportProcessDefinitionMaxPageSize;
  @Value("${camunda.optimize.engine.import.process-definition-xml.page.size.max}")
  private int engineImportProcessDefinitionXmlMaxPageSize;
  @Value("${camunda.optimize.engine.import.activity-instance.page.size.max}")
  private int engineImportActivityInstanceMaxPageSize;
  @Value("${camunda.optimize.engine.import.process-instance.page.size.max}")
  private int engineImportProcessInstanceMaxPageSize;
  @Value("${camunda.optimize.engine.import.variable.page.size.max}")
  private int engineImportVariableInstanceMaxPageSize;

  @Value("${camunda.optimize.engine.import.process-definition.page.size.min}")
  private int engineImportProcessDefinitionMinPageSize;
  @Value("${camunda.optimize.engine.import.process-definition-xml.page.size.min}")
  private int engineImportProcessDefinitionXmlMinPageSize;
  @Value("${camunda.optimize.engine.import.activity-instance.page.size.min}")
  private int engineImportActivityInstanceMinPageSize;

  // plugins
  @Value(("${camunda.optimize.plugin.variable.import.base.packages}"))
  private String variableImportPluginBasePackages;

  @Value("${camunda.optimize.es.host}")
  private String elasticSearchHost;
  @Value("${camunda.optimize.es.port}")
  private Integer elasticSearchPort;
  @Value(("${camunda.optimize.es.connection.timeout.ms}"))
  private int elasticsearchConnectionTimeout;
  @Value(("${camunda.optimize.es.scroll.timeout.ms}"))
  private int elasticsearchScrollTimeout;
  @Value("${camunda.optimize.es.sampler.interval}")
  private long samplerInterval;

  @Value("${camunda.optimize.es.index}")
  private String optimizeIndex;
  @Value("${camunda.optimize.es.event.type}")
  private String eventType;
  @Value("${camunda.optimize.es.process.instance.type}")
  private String processInstanceType;
  @Value("${camunda.optimize.es.process.instance.id.tracking.type}")
  private String processInstanceIdTrackingType;
  @Value("${camunda.optimize.es.variable.type}")
  private String variableType;
  @Value("${camunda.optimize.es.heatmap.duration.target.value.type}")
  private String durationHeatmapTargetValueType;
  @Value("${camunda.optimize.es.procdef.type}")
  private String processDefinitionType;
  @Value("${camunda.optimize.es.procdef.xml.type}")
  private String processDefinitionXmlType;
  @Value("${camunda.optimize.es.users.type}")
  private String elasticSearchUsersType;
  @Value("${camunda.optimize.es.import.index.type}")
  private String importIndexType;
  @Value("${camunda.optimize.es.procdef.import.index.type}")
  private String processDefinitionImportIndexType;
  @Value("${camunda.optimize.es.license.type}")
  private String licenseType;

  @Value("${camunda.optimize.engine.rest}")
  private String engineRestApiEndpoint;
  @Value(("${camunda.optimize.engine.connect.timeout.ms}"))
  private int engineConnectTimeout;
  @Value(("${camunda.optimize.engine.read.timeout.ms}"))
  private int engineReadTimeout;
  @Value("${camunda.optimize.engine.hai.endpoint}")
  private String historicActivityInstanceEndpoint;
  @Value("${camunda.optimize.engine.hai.count.endpoint}")
  private String historicActivityInstanceCountEndpoint;
  @Value("${camunda.optimize.engine.history.variable.endpoint}")
  private String historicVariableInstanceEndpoint;
  @Value("${camunda.optimize.engine.history.variable.count.endpoint}")
  private String historicVariableInstanceCountEndpoint;
  @Value("${camunda.optimize.engine.procdef.endpoint}")
  private String processDefinitionEndpoint;
  @Value("${camunda.optimize.engine.procdef.count.endpoint}")
  private String processDefinitionCountEndpoint;
  @Value("${camunda.optimize.engine.procdef.xml.endpoint}")
  private String processDefinitionXmlEndpoint;
  @Value("${camunda.optimize.engine.history.procinst.endpoint}")
  private String historicProcessInstanceEndpoint;
  @Value("${camunda.optimize.engine.history.procinst.count.endpoint}")
  private String historicProcessInstanceCountEndpoint;
  @Value("${camunda.optimize.engine.user.validation.endpoint}")
  private String userValidationEndpoint;
  @Value("${camunda.optimize.engine.name}")
  private String engineName;
  @Value("${camunda.optimize.engine.enabled}")
  private boolean engineConnected;


  @Value("${camunda.optimize.es.analyzer.name}")
  private String analyzerName;
  @Value("${camunda.optimize.es.analyzer.tokenizer}")
  private String tokenizer;
  @Value("${camunda.optimize.es.analyzer.tokenfilter}")
  private String tokenFilter;
  @Value("${camunda.optimize.es.import.handler.interval.ms}")
  private long importHandlerWait;
  @Value("${camunda.optimize.es.import.handler.pages.reset.interval.hours}")
  private double importResetInterval;
  @Value("${camunda.optimize.es.import.handler.max.backoff}")
  private long maximumBackoff;
  @Value("${camunda.optimize.es.settings.index.refresh_interval}")
  private String esRefreshInterval;
  @Value("${camunda.optimize.es.settings.index.number_of_replicas}")
  private int esNumberOfReplicas;
  @Value("${camunda.optimize.es.settings.index.number_of_shards}")
  private int esNumberOfShards;
  @Value("${camunda.optimize.es.import.handler.general.backoff.ms}")
  private long generalBackoff;


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
    return defaultUser.trim();
  }

  public String getDefaultPassword() {
    return defaultPassword.trim();
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public int getEngineImportMaxPageSize() {
    ensureGreaterThanZero(engineImportMaxPageSize);
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

  public String getHistoricProcessInstanceEndpoint() {
    return historicProcessInstanceEndpoint;
  }

  public double getImportResetInterval() {
    return importResetInterval;
  }

  public int getElasticsearchScrollTimeout() {
    return elasticsearchScrollTimeout;
  }

  public int getElasticsearchConnectionTimeout() {
    return elasticsearchConnectionTimeout;
  }

  public int getEngineConnectTimeout() {
    return engineConnectTimeout;
  }

  public int getEngineReadTimeout() {
    return engineReadTimeout;
  }

  public boolean isEngineConnected() {
    return engineConnected;
  }

  public String getImportIndexType() {
    return importIndexType;
  }

  public String getDurationHeatmapTargetValueType() {
    return durationHeatmapTargetValueType;
  }

  public String getHistoricVariableInstanceEndpoint() {
    return historicVariableInstanceEndpoint;
  }

  public String getVariableType() {
    return variableType;
  }

  public int getMaxVariableValueListSize() {
    return maxVariableValueListSize;
  }

  public String getHistoricVariableInstanceCountEndpoint() {
    return historicVariableInstanceCountEndpoint;
  }

  public String getProcessInstanceType() {
    return processInstanceType;
  }

  public String getHistoricProcessInstanceCountEndpoint() {
    return historicProcessInstanceCountEndpoint;
  }

  public int getEngineImportProcessInstanceMaxPageSize() {
    ensureGreaterThanZero(engineImportProcessInstanceMaxPageSize);
    return engineImportProcessInstanceMaxPageSize;
  }

  public int getEngineImportVariableInstanceMaxPageSize() {
    ensureGreaterThanZero(engineImportVariableInstanceMaxPageSize);
    return engineImportVariableInstanceMaxPageSize;
  }

  public String getProcessDefinitionsToImport() {
    return processDefinitionsToImport;
  }

  public String[] getProcessDefinitionsToImportAsArray() {
    String[] processDefinitionArrayToImport = splitStringByComma(processDefinitionsToImport);
    if(processDefinitionArrayToImport.length == 1 && processDefinitionArrayToImport[0].isEmpty()) {
      return new String[]{};
    }
    return processDefinitionArrayToImport;
  }

  public boolean areProcessDefinitionsToImportDefined() {
    return getProcessDefinitionsToImportAsArray().length > 0;
  }

  public String getProcessDefinitionImportIndexType() {
    return processDefinitionImportIndexType;
  }

  public void setProcessDefinitionsToImport(String processDefinitionsToImport) {
    this.processDefinitionsToImport = processDefinitionsToImport;
  }

  public String getEsRefreshInterval() {
    return esRefreshInterval;
  }

  public int getEsNumberOfReplicas() {
    return esNumberOfReplicas;
  }

  public int getEsNumberOfShards() {
    return esNumberOfShards;
  }

  public void setLifetime(Integer lifetime) {
    this.lifetime = lifetime;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public void setDefaultUser(String defaultUser) {
    this.defaultUser = defaultUser;
  }

  public void setDefaultPassword(String defaultPassword) {
    this.defaultPassword = defaultPassword;
  }

  public void setEngineAuthenticationEnabled(boolean engineAuthenticationEnabled) {
    this.engineAuthenticationEnabled = engineAuthenticationEnabled;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public void setEngineImportMaxPageSize(int engineImportMaxPageSize) {
    this.engineImportMaxPageSize = engineImportMaxPageSize;
  }

  public void setEngineImportProcessDefinitionXmlMaxPageSize(int engineImportProcessDefinitionXmlMaxPageSize) {
    this.engineImportProcessDefinitionXmlMaxPageSize = engineImportProcessDefinitionXmlMaxPageSize;
  }

  public void setEngineImportProcessInstanceMaxPageSize(int engineImportProcessInstanceMaxPageSize) {
    this.engineImportProcessInstanceMaxPageSize = engineImportProcessInstanceMaxPageSize;
  }

  public void setEngineImportVariableInstanceMaxPageSize(int engineImportVariableInstanceMaxPageSize) {
    this.engineImportVariableInstanceMaxPageSize = engineImportVariableInstanceMaxPageSize;
  }

  public void setMaxJobQueueSize(int maxJobQueueSize) {
    this.maxJobQueueSize = maxJobQueueSize;
  }

  public void setImportExecutorThreadCount(int importExecutorThreadCount) {
    this.importExecutorThreadCount = importExecutorThreadCount;
  }

  public void setMaxVariableValueListSize(int maxVariableValueListSize) {
    this.maxVariableValueListSize = maxVariableValueListSize;
  }

  public void setElasticSearchHost(String elasticSearchHost) {
    this.elasticSearchHost = elasticSearchHost;
  }

  public void setElasticSearchPort(Integer elasticSearchPort) {
    this.elasticSearchPort = elasticSearchPort;
  }

  public void setElasticsearchConnectionTimeout(int elasticsearchConnectionTimeout) {
    this.elasticsearchConnectionTimeout = elasticsearchConnectionTimeout;
  }

  public void setElasticsearchScrollTimeout(int elasticsearchScrollTimeout) {
    this.elasticsearchScrollTimeout = elasticsearchScrollTimeout;
  }

  public void setOptimizeIndex(String optimizeIndex) {
    this.optimizeIndex = optimizeIndex;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public void setProcessInstanceType(String processInstanceType) {
    this.processInstanceType = processInstanceType;
  }

  public void setVariableType(String variableType) {
    this.variableType = variableType;
  }

  public void setDurationHeatmapTargetValueType(String durationHeatmapTargetValueType) {
    this.durationHeatmapTargetValueType = durationHeatmapTargetValueType;
  }

  public void setProcessDefinitionType(String processDefinitionType) {
    this.processDefinitionType = processDefinitionType;
  }

  public void setProcessDefinitionXmlType(String processDefinitionXmlType) {
    this.processDefinitionXmlType = processDefinitionXmlType;
  }

  public void setElasticSearchUsersType(String elasticSearchUsersType) {
    this.elasticSearchUsersType = elasticSearchUsersType;
  }

  public void setImportIndexType(String importIndexType) {
    this.importIndexType = importIndexType;
  }

  public void setProcessDefinitionImportIndexType(String processDefinitionImportIndexType) {
    this.processDefinitionImportIndexType = processDefinitionImportIndexType;
  }

  public void setEngineRestApiEndpoint(String engineRestApiEndpoint) {
    this.engineRestApiEndpoint = engineRestApiEndpoint;
  }

  public void setEngineConnectTimeout(int engineConnectTimeout) {
    this.engineConnectTimeout = engineConnectTimeout;
  }

  public void setEngineReadTimeout(int engineReadTimeout) {
    this.engineReadTimeout = engineReadTimeout;
  }

  public void setHistoricActivityInstanceEndpoint(String historicActivityInstanceEndpoint) {
    this.historicActivityInstanceEndpoint = historicActivityInstanceEndpoint;
  }

  public void setHistoricActivityInstanceCountEndpoint(String historicActivityInstanceCountEndpoint) {
    this.historicActivityInstanceCountEndpoint = historicActivityInstanceCountEndpoint;
  }

  public void setHistoricVariableInstanceEndpoint(String historicVariableInstanceEndpoint) {
    this.historicVariableInstanceEndpoint = historicVariableInstanceEndpoint;
  }

  public void setHistoricVariableInstanceCountEndpoint(String historicVariableInstanceCountEndpoint) {
    this.historicVariableInstanceCountEndpoint = historicVariableInstanceCountEndpoint;
  }

  public void setProcessDefinitionEndpoint(String processDefinitionEndpoint) {
    this.processDefinitionEndpoint = processDefinitionEndpoint;
  }

  public void setProcessDefinitionCountEndpoint(String processDefinitionCountEndpoint) {
    this.processDefinitionCountEndpoint = processDefinitionCountEndpoint;
  }

  public void setProcessDefinitionXmlEndpoint(String processDefinitionXmlEndpoint) {
    this.processDefinitionXmlEndpoint = processDefinitionXmlEndpoint;
  }

  public void setHistoricProcessInstanceEndpoint(String historicProcessInstanceEndpoint) {
    this.historicProcessInstanceEndpoint = historicProcessInstanceEndpoint;
  }

  public void setHistoricProcessInstanceCountEndpoint(String historicProcessInstanceCountEndpoint) {
    this.historicProcessInstanceCountEndpoint = historicProcessInstanceCountEndpoint;
  }

  public void setUserValidationEndpoint(String userValidationEndpoint) {
    this.userValidationEndpoint = userValidationEndpoint;
  }

  public void setEngineName(String engineName) {
    this.engineName = engineName;
  }

  public void setEngineConnected(boolean engineConnected) {
    this.engineConnected = engineConnected;
  }

  public void setAnalyzerName(String analyzerName) {
    this.analyzerName = analyzerName;
  }

  public void setTokenizer(String tokenizer) {
    this.tokenizer = tokenizer;
  }

  public void setTokenFilter(String tokenFilter) {
    this.tokenFilter = tokenFilter;
  }

  public void setImportHandlerWait(long importHandlerWait) {
    this.importHandlerWait = importHandlerWait;
  }

  public void setImportResetInterval(double importResetInterval) {
    this.importResetInterval = importResetInterval;
  }

  public void setMaximumBackoff(long maximumBackoff) {
    this.maximumBackoff = maximumBackoff;
  }

  public void setEsRefreshInterval(String esRefreshInterval) {
    this.esRefreshInterval = esRefreshInterval;
  }

  public void setEsNumberOfReplicas(int esNumberOfReplicas) {
    this.esNumberOfReplicas = esNumberOfReplicas;
  }

  public void setEsNumberOfShards(int esNumberOfShards) {
    this.esNumberOfShards = esNumberOfShards;
  }

  public int getEngineImportProcessDefinitionXmlMaxPageSize() {
    return engineImportProcessDefinitionXmlMaxPageSize;
  }

  public String getProcessDefinitionXmlEndpoint() {
    return processDefinitionXmlEndpoint;
  }

  public String getLicenseType() {
    return licenseType;
  }

  public void setLicenseType(String licenseType) {
    this.licenseType = licenseType;
  }

  public long getGeneralBackoff() {
    return generalBackoff;
  }

  public void setGeneralBackoff(long generalBackoff) {
    this.generalBackoff = generalBackoff;
  }

  public long getSamplerInterval() {
    return samplerInterval;
  }

  public void setSamplerInterval(long samplerInterval) {
    this.samplerInterval = samplerInterval;
  }

  public String getVariableImportPluginBasePackages() {
    return variableImportPluginBasePackages;
  }

  public void setVariableImportPluginBasePackages(String variableImportPluginBasePackages) {
    this.variableImportPluginBasePackages = variableImportPluginBasePackages;
  }

  public String[] getVariableImportPluginBasePackagesAsArray() {
    String[] basePackageArray = splitStringByComma(variableImportPluginBasePackages);
    if(basePackageArray.length == 1 && basePackageArray[0].isEmpty()) {
      return new String[]{};
    }
    return basePackageArray;
  }

  public long getElasticsearchConnectionSamplerInterval() {
    return samplerInterval;
  }

  public String getProcessInstanceIdTrackingType() {
    return processInstanceIdTrackingType;
  }

  public void setProcessInstanceIdTrackingType(String processInstanceIdTrackingType) {
    this.processInstanceIdTrackingType = processInstanceIdTrackingType;
  }

  public int getNumberOfRetriesOnConflict() {
    return numberOfRetriesOnConflict;
  }

  public void setNumberOfRetriesOnConflict(int numberOfRetriesOnConflict) {
    this.numberOfRetriesOnConflict = numberOfRetriesOnConflict;
  }

  public int getEngineImportProcessDefinitionMaxPageSize() {
    ensureGreaterThanZero(engineImportProcessDefinitionMaxPageSize);
    return engineImportProcessDefinitionMaxPageSize;
  }

  public void setEngineImportProcessDefinitionMaxPageSize(int engineImportProcessDefinitionMaxPageSize) {
    this.engineImportProcessDefinitionMaxPageSize = engineImportProcessDefinitionMaxPageSize;
  }

  public int getEngineImportActivityInstanceMaxPageSize() {
    ensureGreaterThanZero(engineImportActivityInstanceMaxPageSize);
    return engineImportActivityInstanceMaxPageSize;
  }

  public void setEngineImportActivityInstanceMaxPageSize(int engineImportActivityInstanceMaxPageSize) {
    this.engineImportActivityInstanceMaxPageSize = engineImportActivityInstanceMaxPageSize;
  }

  public int getEngineImportProcessDefinitionMinPageSize() {
    ensureGreaterThanZero(engineImportProcessDefinitionMinPageSize);
    return engineImportProcessDefinitionMinPageSize;
  }

  public void setEngineImportProcessDefinitionMinPageSize(int engineImportProcessDefinitionMinPageSize) {
    this.engineImportProcessDefinitionMinPageSize = engineImportProcessDefinitionMinPageSize;
  }

  public int getEngineImportProcessDefinitionXmlMinPageSize() {
    ensureGreaterThanZero(engineImportProcessDefinitionXmlMinPageSize);
    return engineImportProcessDefinitionXmlMinPageSize;
  }

  public void setEngineImportProcessDefinitionXmlMinPageSize(int engineImportProcessDefinitionXmlMinPageSize) {
    this.engineImportProcessDefinitionXmlMinPageSize = engineImportProcessDefinitionXmlMinPageSize;
  }

  public int getEngineImportActivityInstanceMinPageSize() {
    ensureGreaterThanZero(engineImportActivityInstanceMinPageSize);
    return engineImportActivityInstanceMinPageSize;
  }

  public void setEngineImportActivityInstanceMinPageSize(int engineImportActivityInstanceMinPageSize) {
    this.engineImportActivityInstanceMinPageSize = engineImportActivityInstanceMinPageSize;
  }

  public boolean isDefaultUserCreationEnabled() {
    return defaultUserCreationEnabled;
  }

  public void setDefaultUserCreationEnabled(boolean defaultUserCreationEnabled) {
    this.defaultUserCreationEnabled = defaultUserCreationEnabled;
  }

  public String getDefaultEngineAuthenticationUser() {
    if(defaultEngineAuthenticationUser == null || defaultEngineAuthenticationUser.isEmpty()) {
      return defaultUser.trim();
    }
    return defaultEngineAuthenticationUser.trim();
  }

  public void setDefaultEngineAuthenticationUser(String defaultEngineAuthenticationUser) {
    this.defaultEngineAuthenticationUser = defaultEngineAuthenticationUser;
  }

  public String getDefaultEngineAuthenticationPassword() {
    if(defaultEngineAuthenticationPassword == null || defaultEngineAuthenticationPassword.isEmpty()) {
      return defaultPassword.trim();
    }
    return defaultEngineAuthenticationPassword.trim();
  }

  public void setDefaultEngineAuthenticationPassword(String defaultEngineAuthenticationPassword) {
    this.defaultEngineAuthenticationPassword = defaultEngineAuthenticationPassword;
  }
}
