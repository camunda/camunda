/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import lombok.Setter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.ConfigurationParser.parseConfigFromLocations;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.AVAILABLE_LOCALES;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.FALLBACK_LOCALE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.UI_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.cutTrailingSlash;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.ensureGreaterThanZero;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.getLocationsAsInputStream;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathAsAbsoluteUrl;

@Setter
public class ConfigurationService {
  private static final String ENGINE_REST_PATH = "/engine/";
  private static final String ERROR_NO_ENGINE_WITH_ALIAS = "No Engine configured with alias ";

  // @formatter:off
  private static final TypeRef<HashMap<String, EngineConfiguration>> ENGINES_MAP_TYPEREF =
    new TypeRef<HashMap<String, EngineConfiguration>>() {};
  private static final TypeRef<List<String>> LIST_OF_STRINGS_TYPE_REF = new TypeRef<List<String>>() {};
  // @formatter:on

  private ReadContext configJsonContext;

  private Map<String, EngineConfiguration> configuredEngines;
  private Integer tokenLifeTime;
  private String tokenSecret;
  private Boolean sameSiteCookieFlagEnabled;
  private List<String> superUserIds;

  private String userValidationEndpoint;
  private String processDefinitionEndpoint;
  private String processDefinitionXmlEndpoint;
  private String decisionDefinitionEndpoint;
  private String decisionDefinitionXmlEndpoint;

  private String engineDateFormat;
  private Long initialBackoff;
  private Long maximumBackoff;

  // elasticsearch connection
  private List<ElasticsearchConnectionNodeConfiguration> elasticsearchConnectionNodes;
  private Integer elasticsearchScrollTimeout;
  private Integer elasticsearchConnectionTimeout;
  private ProxyConfiguration elasticSearchProxyConfig;

  // elasticsearch connection security
  private String elasticsearchSecurityUsername;
  private String elasticsearchSecurityPassword;
  private Boolean elasticsearchSecuritySSLEnabled;
  private String elasticsearchSecuritySSLCertificate;
  private List<String> elasticsearchSecuritySSLCertificateAuthorities;

  // elasticsearch cluster settings
  private Integer esNumberOfReplicas;
  private Integer esNumberOfShards;
  private String esRefreshInterval;
  private String esIndexPrefix;

  // elastic query settings
  private Integer esAggregationBucketLimit;

  // job executor settings
  private Integer elasticsearchJobExecutorQueueSize;
  private Integer elasticsearchJobExecutorThreadCount;

  // engine import settings
  private Integer engineConnectTimeout;
  private Integer engineReadTimeout;
  private Integer currentTimeBackoffMilliseconds;
  private Integer engineImportProcessInstanceMaxPageSize;
  private Integer engineImportVariableInstanceMaxPageSize;
  private Integer engineImportProcessDefinitionXmlMaxPageSize;
  private Integer engineImportActivityInstanceMaxPageSize;
  private Integer engineImportUserTaskInstanceMaxPageSize;
  private Integer engineImportIdentityLinkLogsMaxPageSize;
  private Integer engineImportDecisionDefinitionXmlMaxPageSize;
  private Integer engineImportDecisionInstanceMaxPageSize;
  private Integer importIndexAutoStorageIntervalInSec;
  private Boolean importDmnDataEnabled;
  private Boolean importUserTaskWorkerDataEnabled;

  // plugin base packages
  private List<String> variableImportPluginBasePackages;
  private List<String> engineRestFilterPluginBasePackages;
  private List<String> authenticationExtractorPluginBasePackages;
  private List<String> DecisionOutputImportPluginBasePackages;
  private List<String> DecisionInputImportPluginBasePackages;
  private String pluginDirectory;


  private String containerHost;
  private String containerKeystorePassword;
  private String containerKeystoreLocation;
  private Integer containerHttpsPort;

  // we use optional field here in order to allow restoring defaults with BeanUtils.copyProperties
  // if only the getter is of type Optional the value won't get reset properly
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private Optional<String> containerAccessUrl;

  // We use optional field here in order to allow restoring defaults with BeanUtils.copyProperties
  // if only the getter is of type Optional the value won't get reset properly.
  // We also distinguish between null and Optional.empty here,
  // null results in the value getting read from the config json.
  // Optional.empty is an actual value that does not trigger read from configuration json on access.
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private Optional<Integer> containerHttpPort;

  private Integer maxStatusConnections;
  private Boolean checkMetadata;

  private Boolean emailEnabled;
  private String alertEmailUsername;
  private String alertEmailPassword;
  private String alertEmailAddress;
  private String alertEmailHostname;
  private Integer alertEmailPort;
  private String alertEmailProtocol;

  private Integer exportCsvLimit;

  private Properties quartzProperties;

  // history cleanup
  private OptimizeCleanupConfiguration cleanupServiceConfiguration;

  private Boolean sharingEnabled;

  // localization
  private List<String> availableLocales;
  private String fallbackLocale;

  // ui customization
  private UIConfiguration uiConfiguration;

  /**
   * This method is needed so jackson can deserialize/serialize
   * the service configuration.
   * See TestEmbeddedCamundaOptimize for where it's being used.
   */
  @JsonCreator
  static ConfigurationService createDefault() {
    return ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  ConfigurationService(String[] configLocations, ConfigurationValidator configurationValidator) {
    List<InputStream> configStreams = getLocationsAsInputStream(configLocations);
    this.configJsonContext = parseConfigFromLocations(configStreams)
      .orElseThrow(() -> new OptimizeConfigurationException("No single configuration source could be read"));
    Optional.ofNullable(configurationValidator)
      .ifPresent(validator -> validator.validate(this));
  }

  ReadContext getConfigJsonContext() {
    return configJsonContext;
  }

  public Map<String, EngineConfiguration> getConfiguredEngines() {
    if (configuredEngines == null) {
      configuredEngines = configJsonContext.read(ConfigurationServiceConstants.CONFIGURED_ENGINES, ENGINES_MAP_TYPEREF);
    }
    return configuredEngines;
  }

  public Optional<String> getTokenSecret() {
    if (tokenSecret == null) {
      tokenSecret = configJsonContext.read(ConfigurationServiceConstants.TOKEN_SECRET);
    }
    return Optional.ofNullable(tokenSecret);
  }

  @JsonIgnore
  public Integer getTokenLifeTimeMinutes() {
    if (tokenLifeTime == null) {
      tokenLifeTime = configJsonContext.read(ConfigurationServiceConstants.TOKEN_LIFE_TIME, Integer.class);
    }
    return tokenLifeTime;
  }

  public Boolean getSameSiteCookieFlagEnabled() {
    if (sameSiteCookieFlagEnabled == null) {
      sameSiteCookieFlagEnabled =
        configJsonContext.read(ConfigurationServiceConstants.SAME_SITE_COOKIE_FLAG_ENABLED, Boolean.class);
    }
    return sameSiteCookieFlagEnabled;
  }

  public List<String> getSuperUserIds() {
    if (superUserIds == null) {
      superUserIds = configJsonContext.read(ConfigurationServiceConstants.SUPER_USER_IDS, LIST_OF_STRINGS_TYPE_REF);
    }
    return superUserIds;
  }

  public List<ElasticsearchConnectionNodeConfiguration> getElasticsearchConnectionNodes() {
    if (elasticsearchConnectionNodes == null) {
      // @formatter:off
      TypeRef<List<ElasticsearchConnectionNodeConfiguration>> typeRef =
        new TypeRef<List<ElasticsearchConnectionNodeConfiguration>>() {};
      // @formatter:on
      elasticsearchConnectionNodes = configJsonContext.read(
        ConfigurationServiceConstants.ELASTIC_SEARCH_CONNECTION_NODES, typeRef
      );
    }
    return elasticsearchConnectionNodes;
  }

  public List<String> getDecisionOutputImportPluginBasePackages() {
    if (DecisionOutputImportPluginBasePackages == null) {
      DecisionOutputImportPluginBasePackages = configJsonContext.read(
        ConfigurationServiceConstants.DECISION_OUTPUT_IMPORT_PLUGIN_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
      );
    }
    return DecisionOutputImportPluginBasePackages;
  }

  public String getUserValidationEndpoint() {
    if (userValidationEndpoint == null) {
      userValidationEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.USER_VALIDATION_ENDPOINT)
      );
    }
    return userValidationEndpoint;
  }

  public String getProcessDefinitionEndpoint() {
    if (processDefinitionEndpoint == null) {
      processDefinitionEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_ENDPOINT)
      );
    }
    return processDefinitionEndpoint;
  }

  public String getDecisionDefinitionEndpoint() {
    if (decisionDefinitionEndpoint == null) {
      decisionDefinitionEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.DECISION_DEFINITION_ENDPOINT)
      );
    }
    return decisionDefinitionEndpoint;
  }

  public String getDecisionDefinitionXmlEndpoint(String decisionDefinitionId) {
    return getDecisionDefinitionEndpoint() + "/" + decisionDefinitionId + getDecisionDefinitionXmlEndpoint();
  }

  public String getEngineDateFormat() {
    if (engineDateFormat == null) {
      engineDateFormat = configJsonContext.read(ConfigurationServiceConstants.ENGINE_DATE_FORMAT);
    }
    return engineDateFormat;
  }

  public int getImportIndexAutoStorageIntervalInSec() {
    if (importIndexAutoStorageIntervalInSec == null) {
      importIndexAutoStorageIntervalInSec = configJsonContext.read(
        ConfigurationServiceConstants.IMPORT_INDEX_AUTO_STORAGE_INTERVAL, Integer.class
      );
    }
    return importIndexAutoStorageIntervalInSec;
  }

  public long getInitialBackoff() {
    if (initialBackoff == null) {
      initialBackoff = configJsonContext.read(ConfigurationServiceConstants.INITIAL_BACKOFF_INTERVAL, Long.class);
    }
    return initialBackoff;
  }

  public long getMaximumBackoff() {
    if (maximumBackoff == null) {
      maximumBackoff = configJsonContext.read(ConfigurationServiceConstants.MAXIMUM_BACK_OFF, Long.class);
    }
    return maximumBackoff;
  }

  public int getElasticsearchJobExecutorQueueSize() {
    if (elasticsearchJobExecutorQueueSize == null) {
      elasticsearchJobExecutorQueueSize = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_MAX_JOB_QUEUE_SIZE, Integer.class
      );
    }
    return elasticsearchJobExecutorQueueSize;
  }

  public int getElasticsearchJobExecutorThreadCount() {
    if (elasticsearchJobExecutorThreadCount == null) {
      elasticsearchJobExecutorThreadCount = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_IMPORT_EXECUTOR_THREAD_COUNT, Integer.class
      );
    }
    return elasticsearchJobExecutorThreadCount;
  }

  public int getElasticsearchScrollTimeout() {
    if (elasticsearchScrollTimeout == null) {
      elasticsearchScrollTimeout = configJsonContext.read(
        ConfigurationServiceConstants.ELASTIC_SEARCH_SCROLL_TIMEOUT, Integer.class
      );
    }
    return elasticsearchScrollTimeout;
  }

  public int getElasticsearchConnectionTimeout() {
    if (elasticsearchConnectionTimeout == null) {
      elasticsearchConnectionTimeout = configJsonContext.read(
        ConfigurationServiceConstants.ELASTIC_SEARCH_CONNECTION_TIMEOUT, Integer.class
      );
    }
    return elasticsearchConnectionTimeout;
  }

  public ProxyConfiguration getElasticSearchProxyConfig() {
    if (elasticSearchProxyConfig == null) {
      elasticSearchProxyConfig = configJsonContext.read(
        ConfigurationServiceConstants.ELASTIC_SEARCH_PROXY, ProxyConfiguration.class
      );
      elasticSearchProxyConfig.validate();
    }
    return elasticSearchProxyConfig;
  }

  public int getEngineConnectTimeout() {
    if (engineConnectTimeout == null) {
      engineConnectTimeout = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_CONNECT_TIMEOUT, Integer.class
      );
    }
    return engineConnectTimeout;
  }

  public int getEngineReadTimeout() {
    if (engineReadTimeout == null) {
      engineReadTimeout = configJsonContext.read(ConfigurationServiceConstants.ENGINE_READ_TIMEOUT, Integer.class);
    }
    return engineReadTimeout;
  }

  public int getCurrentTimeBackoffMilliseconds() {
    if (currentTimeBackoffMilliseconds == null) {
      currentTimeBackoffMilliseconds = configJsonContext.read(
        ConfigurationServiceConstants.IMPORT_CURRENT_TIME_BACKOFF_MILLISECONDS, Integer.class
      );
    }
    return currentTimeBackoffMilliseconds;
  }

  public int getEngineImportProcessInstanceMaxPageSize() {
    if (engineImportProcessInstanceMaxPageSize == null) {
      engineImportProcessInstanceMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportProcessInstanceMaxPageSize);
    return engineImportProcessInstanceMaxPageSize;
  }

  public int getEngineImportVariableInstanceMaxPageSize() {
    if (engineImportVariableInstanceMaxPageSize == null) {
      engineImportVariableInstanceMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportVariableInstanceMaxPageSize);
    return engineImportVariableInstanceMaxPageSize;
  }

  public int getEsAggregationBucketLimit() {
    if (esAggregationBucketLimit == null) {
      esAggregationBucketLimit = configJsonContext.read(
        ConfigurationServiceConstants.ES_AGGREGATION_BUCKET_LIMIT, Integer.class
      );
    }
    return esAggregationBucketLimit;
  }

  public String getEsRefreshInterval() {
    if (esRefreshInterval == null) {
      esRefreshInterval = configJsonContext.read(ConfigurationServiceConstants.ES_REFRESH_INTERVAL);
    }
    return esRefreshInterval;
  }

  public int getEsNumberOfReplicas() {
    if (esNumberOfReplicas == null) {
      esNumberOfReplicas = configJsonContext.read(ConfigurationServiceConstants.ES_NUMBER_OF_REPLICAS, Integer.class);
    }
    return esNumberOfReplicas;
  }

  public int getEsNumberOfShards() {
    if (esNumberOfShards == null) {
      esNumberOfShards = configJsonContext.read(ConfigurationServiceConstants.ES_NUMBER_OF_SHARDS, Integer.class);
    }
    return esNumberOfShards;
  }

  public String getEsIndexPrefix() {
    if (esIndexPrefix == null) {
      esIndexPrefix = configJsonContext.read(ConfigurationServiceConstants.ES_INDEX_PREFIX, String.class);
    }
    return esIndexPrefix;
  }

  public int getEngineImportProcessDefinitionXmlMaxPageSize() {
    if (engineImportProcessDefinitionXmlMaxPageSize == null) {
      engineImportProcessDefinitionXmlMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE, Integer.class
      );
    }
    return engineImportProcessDefinitionXmlMaxPageSize;
  }

  private String getProcessDefinitionXmlEndpoint() {
    if (processDefinitionXmlEndpoint == null) {
      processDefinitionXmlEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_XML_ENDPOINT)
      );
    }
    return processDefinitionXmlEndpoint;
  }

  public Boolean getSharingEnabled() {
    if (sharingEnabled == null) {
      sharingEnabled = configJsonContext.read(ConfigurationServiceConstants.SHARING_ENABLED, Boolean.class);
    }
    return sharingEnabled;
  }

  private String getDecisionDefinitionXmlEndpoint() {
    if (decisionDefinitionXmlEndpoint == null) {
      decisionDefinitionXmlEndpoint = cutTrailingSlash(
        configJsonContext.read(ConfigurationServiceConstants.DECISION_DEFINITION_XML_ENDPOINT)
      );
    }
    return decisionDefinitionXmlEndpoint;
  }

  public int getEngineImportDecisionDefinitionXmlMaxPageSize() {
    if (engineImportDecisionDefinitionXmlMaxPageSize == null) {
      engineImportDecisionDefinitionXmlMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_DEFINITION_XML_MAX_PAGE_SIZE, Integer.class
      );
    }
    return engineImportDecisionDefinitionXmlMaxPageSize;
  }

  public int getEngineImportDecisionInstanceMaxPageSize() {
    if (engineImportDecisionInstanceMaxPageSize == null) {
      engineImportDecisionInstanceMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_INSTANCE_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportDecisionInstanceMaxPageSize);
    return engineImportDecisionInstanceMaxPageSize;
  }

  public int getEngineImportActivityInstanceMaxPageSize() {
    if (engineImportActivityInstanceMaxPageSize == null) {
      engineImportActivityInstanceMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportActivityInstanceMaxPageSize);
    return engineImportActivityInstanceMaxPageSize;
  }

  public int getEngineImportUserTaskInstanceMaxPageSize() {
    if (engineImportUserTaskInstanceMaxPageSize == null) {
      engineImportUserTaskInstanceMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_USER_TASK_INSTANCE_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportUserTaskInstanceMaxPageSize);
    return engineImportUserTaskInstanceMaxPageSize;
  }

  public int getEngineImportIdentityLinkLogsMaxPageSize() {
    if (engineImportIdentityLinkLogsMaxPageSize == null) {
      engineImportIdentityLinkLogsMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_IDENTITY_LING_LOG_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportIdentityLinkLogsMaxPageSize);
    return engineImportIdentityLinkLogsMaxPageSize;
  }

  public String getPluginDirectory() {
    if (pluginDirectory == null) {
      pluginDirectory = configJsonContext.read(ConfigurationServiceConstants.PLUGIN_BASE_DIRECTORY, String.class);
    }
    return pluginDirectory;
  }

  public List<String> getVariableImportPluginBasePackages() {
    if (variableImportPluginBasePackages == null) {
      variableImportPluginBasePackages = configJsonContext.read(
        ConfigurationServiceConstants.VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
      );
    }
    return variableImportPluginBasePackages;
  }

  public List<String> getEngineRestFilterPluginBasePackages() {
    if (engineRestFilterPluginBasePackages == null) {
      engineRestFilterPluginBasePackages = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_REST_FILTER_PLUGIN_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
      );
    }
    return engineRestFilterPluginBasePackages;
  }

  public List<String> getAuthenticationExtractorPluginBasePackages() {
    if (authenticationExtractorPluginBasePackages == null) {
      authenticationExtractorPluginBasePackages = configJsonContext.read(
        ConfigurationServiceConstants.AUTHENTICATION_EXTRACTOR_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
      );
    }
    return authenticationExtractorPluginBasePackages;
  }

  public String getContainerHost() {
    if (containerHost == null) {
      containerHost = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_HOST);
    }
    return containerHost;
  }

  public String getContainerKeystorePassword() {
    if (containerKeystorePassword == null) {
      containerKeystorePassword = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_PASSWORD);
    }
    return containerKeystorePassword;
  }

  @SuppressWarnings("OptionalAssignedToNull")
  public Optional<String> getContainerAccessUrl() {
    if (containerAccessUrl == null) {
      containerAccessUrl =
        Optional.ofNullable(configJsonContext.read(ConfigurationServiceConstants.CONTAINER_ACCESSURL));
    }
    return containerAccessUrl;
  }

  // Note: special setter for Optional field value, see note on field why the field is Optional
  public void setContainerAccessUrlValue(String containerAccessUrl) {
    this.containerAccessUrl = Optional.ofNullable(containerAccessUrl);
  }

  public List<String> getDecisionInputImportPluginBasePackages() {
    if (DecisionInputImportPluginBasePackages == null) {
      DecisionInputImportPluginBasePackages =
        configJsonContext.read(
          ConfigurationServiceConstants.DECISION_INPUT_IMPORT_PLUGIN_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
        );
    }
    return DecisionInputImportPluginBasePackages;
  }

  public String getContainerKeystoreLocation() {
    if (containerKeystoreLocation == null) {
      containerKeystoreLocation = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_LOCATION);
      // we need external form here for the path to work if the keystore is inside the jar (default)
      containerKeystoreLocation = ConfigurationUtil.resolvePathAsAbsoluteUrl(containerKeystoreLocation)
        .toExternalForm();
    }
    return containerKeystoreLocation;
  }

  public Integer getContainerHttpsPort() {
    if (containerHttpsPort == null) {
      containerHttpsPort = configJsonContext.read(
        ConfigurationServiceConstants.CONTAINER_HTTPS_PORT, Integer.class
      );
      if (containerHttpsPort == null) {
        throw new OptimizeConfigurationException("Optimize container https port is not allowed to be null!");
      }
    }
    return containerHttpsPort;
  }

  public Optional<Integer> getContainerHttpPort() {
    // containerHttpPort is optional so we can adjust
    // it during tests. Thus it is still null initially
    // and need to be checked therefore.
    //noinspection OptionalAssignedToNull
    if (containerHttpPort == null) {
      containerHttpPort = Optional.ofNullable(
        configJsonContext.read(ConfigurationServiceConstants.CONTAINER_HTTP_PORT, Integer.class)
      );
    }
    return containerHttpPort;
  }

  // Note: special setter for Optional field value, see note on field why the field is Optional
  public void setContainerHttpPortValue(Integer containerHttpPort) {
    this.containerHttpPort = Optional.ofNullable(containerHttpPort);
  }

  @JsonIgnore
  public boolean isHttpDisabled() {
    return !getContainerHttpPort().isPresent();
  }

  public int getMaxStatusConnections() {
    if (maxStatusConnections == null) {
      maxStatusConnections = configJsonContext.read(
        ConfigurationServiceConstants.CONTAINER_STATUS_MAX_CONNECTIONS, Integer.class
      );
    }
    return maxStatusConnections;
  }

  public String getProcessDefinitionXmlEndpoint(String processDefinitionId) {
    return getProcessDefinitionEndpoint() + "/" + processDefinitionId + getProcessDefinitionXmlEndpoint();
  }

  public Optional<String> getEngineDefaultTenantIdOfCustomEngine(String engineAlias) {
    return getEngineConfiguration(engineAlias)
      .map(EngineConfiguration::getDefaultTenantId)
      .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public String getEngineRestApiEndpointOfCustomEngine(String engineAlias) {
    return this.getEngineRestApiEndpoint(engineAlias) + ENGINE_REST_PATH + getEngineName(engineAlias);
  }

  public String getDefaultEngineAuthenticationUser(String engineAlias) {
    return getEngineConfiguration(engineAlias)
      .map(EngineConfiguration::getAuthentication)
      .map(EngineAuthenticationConfiguration::getUser)
      .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public String getDefaultEngineAuthenticationPassword(String engineAlias) {
    return getEngineConfiguration(engineAlias)
      .map(EngineConfiguration::getAuthentication)
      .map(EngineAuthenticationConfiguration::getPassword)
      .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  /**
   * This method is mostly for internal usage. All API invocations
   * should rely on
   * {@link #getEngineRestApiEndpointOfCustomEngine(String)}
   *
   * @param engineAlias - an alias of configured engine
   * @return <b>raw</b> REST endpoint, without engine suffix
   */
  private String getEngineRestApiEndpoint(String engineAlias) {
    return getEngineConfiguration(engineAlias)
      .map(EngineConfiguration::getRest)
      .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public String getEngineName(String engineAlias) {
    return getEngineConfiguration(engineAlias).map(EngineConfiguration::getName)
      .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public boolean isEngineImportEnabled(String engineAlias) {
    return getEngineConfiguration(engineAlias).map(EngineConfiguration::isImportEnabled)
      .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public Optional<EngineConfiguration> getEngineConfiguration(String engineAlias) {
    return Optional.ofNullable(this.getConfiguredEngines().get(engineAlias));
  }

  public Properties getQuartzProperties() {
    if (quartzProperties == null) {
      quartzProperties = new Properties();
      quartzProperties.put(
        "org.quartz.jobStore.class", configJsonContext.read(ConfigurationServiceConstants.QUARTZ_JOB_STORE_CLASS)
      );
    }
    return quartzProperties;
  }

  public String getAlertEmailUsername() {
    if (alertEmailUsername == null) {
      alertEmailUsername = configJsonContext.read(ConfigurationServiceConstants.EMAIL_USERNAME);
    }
    return alertEmailUsername;
  }

  public String getAlertEmailPassword() {
    if (alertEmailPassword == null) {
      alertEmailPassword = configJsonContext.read(ConfigurationServiceConstants.EMAIL_PASSWORD);
    }
    return alertEmailPassword;
  }

  public boolean getEmailEnabled() {
    if (emailEnabled == null) {
      emailEnabled = configJsonContext.read(ConfigurationServiceConstants.EMAIL_ENABLED, Boolean.class);
    }
    return emailEnabled;
  }

  public Boolean getImportDmnDataEnabled() {
    if (importDmnDataEnabled == null) {
      importDmnDataEnabled = configJsonContext.read(ConfigurationServiceConstants.IMPORT_DMN_DATA, Boolean.class);
    }
    return importDmnDataEnabled;
  }

  public Boolean getImportUserTaskWorkerDataEnabled() {
    if (importUserTaskWorkerDataEnabled == null) {
      importUserTaskWorkerDataEnabled = configJsonContext.read(
        ConfigurationServiceConstants.IMPORT_USER_TASK_WORKER_DATA,
        Boolean.class
      );
    }
    return importUserTaskWorkerDataEnabled;
  }

  public String getAlertEmailAddress() {
    if (alertEmailAddress == null) {
      alertEmailAddress = configJsonContext.read(ConfigurationServiceConstants.EMAIL_ADDRESS);
    }
    return alertEmailAddress;
  }

  public String getAlertEmailHostname() {
    if (alertEmailHostname == null) {
      alertEmailHostname = configJsonContext.read(ConfigurationServiceConstants.EMAIL_HOSTNAME);
    }
    return alertEmailHostname;
  }

  public Integer getAlertEmailPort() {
    if (alertEmailPort == null) {
      alertEmailPort = configJsonContext.read(ConfigurationServiceConstants.EMAIL_PORT, Integer.class);
    }
    return alertEmailPort;
  }

  public String getAlertEmailProtocol() {
    if (alertEmailProtocol == null) {
      alertEmailProtocol = configJsonContext.read(ConfigurationServiceConstants.EMAIL_PROTOCOL);
    }
    return alertEmailProtocol;
  }

  public Integer getExportCsvLimit() {
    if (exportCsvLimit == null) {
      exportCsvLimit = configJsonContext.read(ConfigurationServiceConstants.EXPORT_CSV_LIMIT, Integer.class);
    }
    return exportCsvLimit;
  }

  public String getElasticsearchSecurityUsername() {
    if (elasticsearchSecurityUsername == null) {
      elasticsearchSecurityUsername =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_USERNAME);
    }
    return elasticsearchSecurityUsername;
  }

  public String getElasticsearchSecurityPassword() {
    if (elasticsearchSecurityPassword == null) {
      elasticsearchSecurityPassword =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_PASSWORD);
    }
    return elasticsearchSecurityPassword;
  }

  public Boolean getElasticsearchSecuritySSLEnabled() {
    if (elasticsearchSecuritySSLEnabled == null) {
      elasticsearchSecuritySSLEnabled =
        configJsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_SSL_ENABLED, Boolean.class);
    }
    return elasticsearchSecuritySSLEnabled;
  }

  public String getElasticsearchSecuritySSLCertificate() {
    if (elasticsearchSecuritySSLCertificate == null && getElasticsearchSecuritySSLEnabled()) {
      elasticsearchSecuritySSLCertificate = configJsonContext.read(
        ConfigurationServiceConstants.ELASTIC_SEARCH_SECURITY_SSL_CERTIFICATE
      );
      if (elasticsearchSecuritySSLCertificate != null) {
        elasticsearchSecuritySSLCertificate = resolvePathAsAbsoluteUrl(elasticsearchSecuritySSLCertificate).getPath();
      }
    }
    return elasticsearchSecuritySSLCertificate;
  }

  public List<String> getElasticsearchSecuritySSLCertificateAuthorities() {
    if (elasticsearchSecuritySSLCertificateAuthorities == null && getElasticsearchSecuritySSLEnabled()) {
      List<String> authoritiesAsList = configJsonContext.read(
        ELASTIC_SEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES, LIST_OF_STRINGS_TYPE_REF
      );
      elasticsearchSecuritySSLCertificateAuthorities = authoritiesAsList.stream()
        .map(a -> resolvePathAsAbsoluteUrl(a).getPath())
        .collect(Collectors.toList());
    }
    return Optional.ofNullable(elasticsearchSecuritySSLCertificateAuthorities).orElse(new ArrayList<>());
  }

  public OptimizeCleanupConfiguration getCleanupServiceConfiguration() {
    if (cleanupServiceConfiguration == null) {
      cleanupServiceConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.HISTORY_CLEANUP,
        OptimizeCleanupConfiguration.class
      );
      cleanupServiceConfiguration.validate();
    }
    return cleanupServiceConfiguration;
  }

  public List<String> getAvailableLocales() {
    if (availableLocales == null) {
      availableLocales = configJsonContext.read(
        AVAILABLE_LOCALES, LIST_OF_STRINGS_TYPE_REF
      );
      if (availableLocales == null || availableLocales.isEmpty()) {
        throw new OptimizeConfigurationException(AVAILABLE_LOCALES + " is not allowed to be empty");
      }
    }
    return availableLocales;
  }

  public String getFallbackLocale() {
    if (fallbackLocale == null) {
      fallbackLocale = configJsonContext.read(FALLBACK_LOCALE, String.class);
      if (StringUtils.isEmpty(fallbackLocale)) {
        throw new OptimizeConfigurationException(FALLBACK_LOCALE + " is not allowed to be empty");
      }
    }
    return fallbackLocale;
  }

  public UIConfiguration getUiConfiguration() {
    if (uiConfiguration == null) {
      uiConfiguration = configJsonContext.read(UI_CONFIGURATION, UIConfiguration.class);
    }
    return uiConfiguration;
  }

}
