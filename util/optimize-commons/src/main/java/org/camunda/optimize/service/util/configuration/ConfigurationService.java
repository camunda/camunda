/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.SchedulerConfig;
import org.camunda.optimize.dto.optimize.ZeebeConfigDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.analytics.AnalyticsConfiguration;
import org.camunda.optimize.service.util.configuration.archive.DataArchiveConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EventIngestionConfiguration;
import org.camunda.optimize.service.util.configuration.engine.UserIdentityCacheConfiguration;
import org.camunda.optimize.service.util.configuration.engine.UserTaskIdentityCacheConfiguration;
import org.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import org.camunda.optimize.service.util.configuration.security.SecurityConfiguration;
import org.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import org.camunda.optimize.service.util.configuration.users.UsersConfiguration;
import org.camunda.optimize.util.SuppressionConstants;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.ConfigurationParser.parseConfigFromLocations;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ANALYTICS_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.AVAILABLE_LOCALES;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CACHES_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.EVENT_BASED_PROCESS_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.EXTERNAL_VARIABLE_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.FALLBACK_LOCALE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IDENTITY_SYNC_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IMPORT_USER_TASK_IDENTITY_META_DATA;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ONBOARDING_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPTIMIZE_API_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.TELEMETRY_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.UI_CONFIGURATION;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.ensureGreaterThanZero;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.getLocationsAsInputStream;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathAsAbsoluteUrl;
import static org.camunda.optimize.util.SuppressionConstants.OPTIONAL_ASSIGNED_TO_NULL;
import static org.camunda.optimize.util.SuppressionConstants.OPTIONAL_FIELD_OR_PARAM;

@Setter
public class ConfigurationService {
  private static final String ERROR_NO_ENGINE_WITH_ALIAS = "No Engine configured with alias ";

  // @formatter:off
  private static final TypeRef<HashMap<String, EngineConfiguration>> ENGINES_MAP_TYPEREF = new TypeRef<>() {};
  private static final TypeRef<List<String>> LIST_OF_STRINGS_TYPE_REF = new TypeRef<>() {};
  private static final TypeRef<HashMap<String, WebhookConfiguration>> WEBHOOKS_MAP_TYPEREF = new TypeRef<>() {};
  // @formatter:on

  private ReadContext configJsonContext;

  private SecurityConfiguration securityConfiguration;

  private DataArchiveConfiguration dataArchiveConfiguration;

  private UsersConfiguration usersConfiguration;

  private Map<String, EngineConfiguration> configuredEngines;
  private ZeebeConfiguration configuredZeebe;

  private String engineDateFormat;
  private Long initialBackoff;
  private Long maximumBackoff;

  // elasticsearch connection
  private List<ElasticsearchConnectionNodeConfiguration> elasticsearchConnectionNodes;
  private Integer esScrollTimeoutInSeconds;
  private Integer elasticsearchConnectionTimeout;
  private Integer elasticsearchResponseConsumerBufferLimitInMb;
  private String elasticsearchPathPrefix;
  private ProxyConfiguration elasticsearchProxyConfig;
  private Boolean elasticsearchSkipHostnameVerification;

  // elasticsearch connection security
  private String elasticsearchSecurityUsername;
  private String elasticsearchSecurityPassword;
  private Boolean elasticsearchSecuritySSLEnabled;
  private Boolean elasticsearchSecuritySslSelfSigned;
  private String elasticsearchSecuritySSLCertificate;
  private List<String> elasticsearchSecuritySSLCertificateAuthorities;

  // elasticsearch cluster settings
  private Integer esNumberOfReplicas;
  private Integer esNumberOfShards;
  private String esRefreshInterval;
  private String esIndexPrefix;
  private Integer esNestedDocumentsLimit;

  // elasticsearch snapshot settings
  private String esSnapshotRepositoryName;

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
  private Boolean engineImportVariableIncludeObjectVariableValue;
  private Integer engineImportProcessDefinitionXmlMaxPageSize;
  private Integer engineImportProcessDefinitionMaxPageSize;
  private Integer engineImportActivityInstanceMaxPageSize;
  private Integer engineImportIncidentMaxPageSize;
  private Integer engineImportUserTaskInstanceMaxPageSize;
  private Integer engineImportIdentityLinkLogsMaxPageSize;
  private Integer engineImportUserOperationLogsMaxPageSize;
  private Integer engineImportDecisionDefinitionXmlMaxPageSize;
  private Integer engineImportDecisionDefinitionMaxPageSize;
  private Integer engineImportDecisionInstanceMaxPageSize;
  private Integer engineImportTenantMaxPageSize;
  private Integer engineImportGroupMaxPageSize;
  private Integer engineImportAuthorizationMaxPageSize;
  private Integer importIndexAutoStorageIntervalInSec;
  private Boolean importDmnDataEnabled;
  private Boolean importUserTaskWorkerDataEnabled;
  private Boolean skipDataAfterNestedDocLimitReached;
  private Boolean customerOnboarding;

  // plugin base packages
  private List<String> variableImportPluginBasePackages;
  private List<String> engineRestFilterPluginBasePackages;
  private List<String> authenticationExtractorPluginBasePackages;
  private List<String> decisionOutputImportPluginBasePackages;
  private List<String> decisionInputImportPluginBasePackages;
  private List<String> businessKeyImportPluginBasePackages;
  private List<String> elasticsearchCustomHeaderPluginBasePackages;
  private String pluginDirectory;

  private String containerHost;
  private String contextPath;
  private String containerKeystorePassword;
  private String containerKeystoreLocation;
  private Integer containerHttpsPort;

  // we use optional field here in order to allow restoring defaults with BeanUtils.copyProperties
  // if only the getter is of type Optional the value won't get reset properly
  @SuppressWarnings(OPTIONAL_FIELD_OR_PARAM)
  private Optional<String> containerAccessUrl;

  // We use optional field here in order to allow restoring defaults with BeanUtils.copyProperties
  // if only the getter is of type Optional the value won't get reset properly.
  // We also distinguish between null and Optional.empty here,
  // null results in the value getting read from the config json.
  // Optional.empty is an actual value that does not trigger read from configuration json on access.
  @SuppressWarnings(OPTIONAL_FIELD_OR_PARAM)
  private Optional<Integer> containerHttpPort;

  private Integer maxStatusConnections;

  private Boolean emailEnabled;
  private String notificationEmailAddress;
  private String notificationEmailHostname;
  private Integer notificationEmailPort;
  private String notificationEmailCompanyBranding;
  private EmailAuthenticationConfiguration emailAuthenticationConfiguration;

  private Map<String, WebhookConfiguration> configuredWebhooks;

  private String digestCronTrigger;

  private EntityConfiguration entityConfiguration;

  private CsvConfiguration csvConfiguration;

  private Properties quartzProperties;

  // history cleanup
  private CleanupConfiguration cleanupServiceConfiguration;

  private Boolean sharingEnabled;

  // localization
  private List<String> availableLocales;
  private String fallbackLocale;

  // ui customization
  private UIConfiguration uiConfiguration;

  private UserTaskIdentityCacheConfiguration userTaskIdentityCacheConfiguration;
  private UserIdentityCacheConfiguration userIdentityCacheConfiguration;

  private EventBasedProcessConfiguration eventBasedProcessConfiguration;

  private TelemetryConfiguration telemetryConfiguration;

  private ExternalVariableConfiguration externalVariableConfiguration;

  private GlobalCacheConfiguration caches;

  private AnalyticsConfiguration analytics;

  private OptimizeApiConfiguration optimizeApiConfiguration;

  private OnboardingConfiguration onboarding;

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

  public ZeebeConfiguration getConfiguredZeebe() {
    if (configuredZeebe == null) {
      configuredZeebe = configJsonContext.read(
        ConfigurationServiceConstants.CONFIGURED_ZEEBE,
        ZeebeConfiguration.class
      );
    }
    return configuredZeebe;
  }

  public SecurityConfiguration getSecurityConfiguration() {
    if (securityConfiguration == null) {
      securityConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.SECURITY,
        SecurityConfiguration.class
      );
    }
    return securityConfiguration;
  }

  public DataArchiveConfiguration getDataArchiveConfiguration() {
    if (dataArchiveConfiguration == null) {
      dataArchiveConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.DATA_ARCHIVE,
        DataArchiveConfiguration.class
      );
    }
    return dataArchiveConfiguration;
  }

  public UsersConfiguration getUsersConfiguration() {
    if (usersConfiguration == null) {
      usersConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.USERS,
        UsersConfiguration.class
      );
    }
    return usersConfiguration;
  }

  @JsonIgnore
  public AuthConfiguration getAuthConfiguration() {
    return getSecurityConfiguration().getAuth();
  }

  public List<ElasticsearchConnectionNodeConfiguration> getElasticsearchConnectionNodes() {
    if (elasticsearchConnectionNodes == null) {
      // @formatter:off
      TypeRef<List<ElasticsearchConnectionNodeConfiguration>> typeRef =
        new TypeRef<>() {};
      // @formatter:on
      elasticsearchConnectionNodes = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_CONNECTION_NODES, typeRef
      );
    }
    return elasticsearchConnectionNodes;
  }

  @JsonIgnore
  public ElasticsearchConnectionNodeConfiguration getFirstElasticsearchConnectionNode() {
    return getElasticsearchConnectionNodes().get(0);
  }

  public List<String> getDecisionOutputImportPluginBasePackages() {
    if (decisionOutputImportPluginBasePackages == null) {
      decisionOutputImportPluginBasePackages = configJsonContext.read(
        ConfigurationServiceConstants.DECISION_OUTPUT_IMPORT_PLUGIN_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
      );
    }
    return decisionOutputImportPluginBasePackages;
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

  public int getEsScrollTimeoutInSeconds() {
    if (esScrollTimeoutInSeconds == null) {
      esScrollTimeoutInSeconds = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_SCROLL_TIMEOUT_IN_SECONDS, Integer.class
      );
    }
    return esScrollTimeoutInSeconds;
  }

  public int getElasticsearchConnectionTimeout() {
    if (elasticsearchConnectionTimeout == null) {
      elasticsearchConnectionTimeout = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_CONNECTION_TIMEOUT, Integer.class
      );
    }
    return elasticsearchConnectionTimeout;
  }

  public int getElasticsearchResponseConsumerBufferLimitInMb() {
    if (elasticsearchResponseConsumerBufferLimitInMb == null) {
      elasticsearchResponseConsumerBufferLimitInMb = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_RESPONSE_CONSUMER_BUFFER_LIMIT_MB, Integer.class
      );
    }
    return elasticsearchResponseConsumerBufferLimitInMb;
  }

  public ProxyConfiguration getElasticsearchProxyConfig() {
    if (elasticsearchProxyConfig == null) {
      elasticsearchProxyConfig = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_PROXY, ProxyConfiguration.class
      );
      elasticsearchProxyConfig.validate();
    }
    return elasticsearchProxyConfig;
  }

  public boolean getElasticsearchSkipHostnameVerification() {
    if (elasticsearchSkipHostnameVerification == null) {
      elasticsearchSkipHostnameVerification = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_SKIP_HOSTNAME_VERIFICATION, Boolean.class
      );
    }
    return elasticsearchSkipHostnameVerification;
  }

  public String getElasticsearchPathPrefix() {
    if (elasticsearchPathPrefix == null) {
      elasticsearchPathPrefix = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_PATH_PREFIX, String.class
      );
    }
    return elasticsearchPathPrefix;
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

  public boolean getEngineImportVariableIncludeObjectVariableValue() {
    if (engineImportVariableIncludeObjectVariableValue == null) {
      engineImportVariableIncludeObjectVariableValue = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_VARIABLE_INCLUDE_OBJECT_VARIABLE_VALUE, Boolean.class
      );
    }
    return engineImportVariableIncludeObjectVariableValue;
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

  public int getEsNestedDocumentsLimit() {
    if (esNestedDocumentsLimit == null) {
      esNestedDocumentsLimit = configJsonContext.read(
        ConfigurationServiceConstants.ES_INDEX_NESTED_DOCUMENTS_LIMIT, Integer.class
      );
    }
    ensureGreaterThanZero(esNestedDocumentsLimit);
    return esNestedDocumentsLimit;
  }

  public String getEsSnapshotRepositoryName() {
    if (esSnapshotRepositoryName == null) {
      esSnapshotRepositoryName = configJsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_SNAPSHOT_REPO, String.class);
    }
    return esSnapshotRepositoryName;
  }

  public int getEngineImportProcessDefinitionXmlMaxPageSize() {
    if (engineImportProcessDefinitionXmlMaxPageSize == null) {
      engineImportProcessDefinitionXmlMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportProcessDefinitionXmlMaxPageSize);
    return engineImportProcessDefinitionXmlMaxPageSize;
  }

  public int getEngineImportProcessDefinitionMaxPageSize() {
    if (engineImportProcessDefinitionMaxPageSize == null) {
      engineImportProcessDefinitionMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportProcessDefinitionMaxPageSize);
    return engineImportProcessDefinitionMaxPageSize;
  }

  public boolean getSharingEnabled() {
    if (sharingEnabled == null) {
      sharingEnabled = configJsonContext.read(ConfigurationServiceConstants.SHARING_ENABLED, Boolean.class);
    }
    return Optional.ofNullable(sharingEnabled).orElse(false);
  }

  public int getEngineImportDecisionDefinitionXmlMaxPageSize() {
    if (engineImportDecisionDefinitionXmlMaxPageSize == null) {
      engineImportDecisionDefinitionXmlMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_DEFINITION_XML_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportDecisionDefinitionXmlMaxPageSize);
    return engineImportDecisionDefinitionXmlMaxPageSize;
  }

  public int getEngineImportDecisionDefinitionMaxPageSize() {
    if (engineImportDecisionDefinitionMaxPageSize == null) {
      engineImportDecisionDefinitionMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_DEFINITION_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportDecisionDefinitionMaxPageSize);
    return engineImportDecisionDefinitionMaxPageSize;
  }

  public boolean getCustomerOnboardingImport() {
    if (customerOnboarding == null) {
      customerOnboarding = configJsonContext.read(ConfigurationServiceConstants.CUSTOMER_ONBOARDING_DATA, Boolean.class);
    }
    return customerOnboarding;
  }

  public void setCustomerOnboardingImport(boolean isActive) {
    this.customerOnboarding = isActive;
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

  public int getEngineImportTenantMaxPageSize() {
    if (engineImportTenantMaxPageSize == null) {
      engineImportTenantMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_TENANT_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportTenantMaxPageSize);
    return engineImportTenantMaxPageSize;
  }

  public int getEngineImportGroupMaxPageSize() {
    if (engineImportGroupMaxPageSize == null) {
      engineImportGroupMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_GROUP_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportGroupMaxPageSize);
    return engineImportGroupMaxPageSize;
  }

  public int getEngineImportAuthorizationMaxPageSize() {
    if (engineImportAuthorizationMaxPageSize == null) {
      engineImportAuthorizationMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_AUTHORIZATION_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportAuthorizationMaxPageSize);
    return engineImportAuthorizationMaxPageSize;
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

  public int getEngineImportIncidentMaxPageSize() {
    if (engineImportIncidentMaxPageSize == null) {
      engineImportIncidentMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_INCIDENT_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportIncidentMaxPageSize);
    return engineImportIncidentMaxPageSize;
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

  public int getEngineImportUserOperationLogsMaxPageSize() {
    if (engineImportUserOperationLogsMaxPageSize == null) {
      engineImportUserOperationLogsMaxPageSize = configJsonContext.read(
        ConfigurationServiceConstants.ENGINE_IMPORT_USER_OPERATION_LOG_MAX_PAGE_SIZE, Integer.class
      );
    }
    ensureGreaterThanZero(engineImportUserOperationLogsMaxPageSize);
    return engineImportUserOperationLogsMaxPageSize;
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

  public List<String> getBusinessKeyImportPluginBasePackages() {
    if (businessKeyImportPluginBasePackages == null) {
      businessKeyImportPluginBasePackages = configJsonContext.read(
        ConfigurationServiceConstants.BUSINESS_KEY_IMPORT_PLUGIN_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
      );
    }
    return businessKeyImportPluginBasePackages;
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

  public List<String> getElasticsearchCustomHeaderPluginBasePackages() {
    if (elasticsearchCustomHeaderPluginBasePackages == null) {
      elasticsearchCustomHeaderPluginBasePackages = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_CUSTOM_HEADER_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
      );
    }
    return elasticsearchCustomHeaderPluginBasePackages;
  }

  public String getContainerHost() {
    if (containerHost == null) {
      containerHost = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_HOST);
    }
    return containerHost;
  }

  public Optional<String> getContextPath() {
    if (contextPath == null) {
      contextPath = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_CONTEXT_PATH);
    }
    return Optional.ofNullable(contextPath);
  }

  public String getContainerKeystorePassword() {
    if (containerKeystorePassword == null) {
      containerKeystorePassword = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_PASSWORD);
    }
    return containerKeystorePassword;
  }

  @SuppressWarnings(OPTIONAL_ASSIGNED_TO_NULL)
  public Optional<String> getContainerAccessUrl() {
    if (containerAccessUrl == null) {
      containerAccessUrl =
        Optional.ofNullable(configJsonContext.read(ConfigurationServiceConstants.CONTAINER_ACCESS_URL));
    }
    return containerAccessUrl;
  }

  // Note: special setter for Optional field value, see note on field why the field is Optional

  public void setContainerAccessUrlValue(String containerAccessUrl) {
    this.containerAccessUrl = Optional.ofNullable(containerAccessUrl);
  }

  public List<String> getDecisionInputImportPluginBasePackages() {
    if (decisionInputImportPluginBasePackages == null) {
      decisionInputImportPluginBasePackages =
        configJsonContext.read(
          ConfigurationServiceConstants.DECISION_INPUT_IMPORT_PLUGIN_BASE_PACKAGES, LIST_OF_STRINGS_TYPE_REF
        );
    }
    return decisionInputImportPluginBasePackages;
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
  @SuppressWarnings(SuppressionConstants.UNUSED)
  public void setContainerHttpPortValue(Integer containerHttpPort) {
    this.containerHttpPort = Optional.ofNullable(containerHttpPort);
  }

  public int getMaxStatusConnections() {
    if (maxStatusConnections == null) {
      maxStatusConnections = configJsonContext.read(
        ConfigurationServiceConstants.CONTAINER_STATUS_MAX_CONNECTIONS, Integer.class
      );
    }
    return maxStatusConnections;
  }

  public Optional<String> getEngineDefaultTenantIdOfCustomEngine(String engineAlias) {
    return getEngineConfiguration(engineAlias)
      .map(EngineConfiguration::getDefaultTenantId)
      .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public List<String> getExcludedTenants(String engineAlias) {
    return getEngineConfiguration(engineAlias)
      .map(EngineConfiguration::getExcludedTenants)
      .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public String getEngineRestApiEndpointOfCustomEngine(String engineAlias) {
    return this.getEngineRestApiEndpoint(engineAlias) + "/engine/" + getEngineName(engineAlias);
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

  public boolean isImportEnabled(final SchedulerConfig dataSourceDto) {
    if (dataSourceDto instanceof EngineDataSourceDto) {
      final EngineDataSourceDto engineSource = (EngineDataSourceDto) dataSourceDto;
      return getEngineConfiguration(engineSource.getName()).map(EngineConfiguration::isImportEnabled)
        .orElseThrow(() -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineSource.getName()));
    } else if (dataSourceDto instanceof ZeebeConfigDto) {
      return getConfiguredZeebe().isEnabled();
    } else if (dataSourceDto instanceof IngestedDataSourceDto) {
      return getExternalVariableConfiguration().getImportConfiguration().isEnabled();
    }
    throw new OptimizeConfigurationException("Invalid data import source");
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

  public boolean getEmailEnabled() {
    if (emailEnabled == null) {
      emailEnabled = configJsonContext.read(ConfigurationServiceConstants.EMAIL_ENABLED, Boolean.class);
    }
    return emailEnabled;
  }

  public EmailAuthenticationConfiguration getEmailAuthenticationConfiguration() {
    if (emailAuthenticationConfiguration == null) {
      emailAuthenticationConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.EMAIL_AUTHENTICATION,
        EmailAuthenticationConfiguration.class
      );
    }
    return emailAuthenticationConfiguration;
  }

  private Boolean getImportDmnDataEnabled() {
    if (importDmnDataEnabled == null) {
      importDmnDataEnabled = configJsonContext.read(ConfigurationServiceConstants.IMPORT_DMN_DATA, Boolean.class);
    }
    return importDmnDataEnabled;
  }

  @JsonIgnore
  public boolean isImportDmnDataEnabled() {
    return getImportDmnDataEnabled();
  }

  private Boolean getImportUserTaskWorkerDataEnabled() {
    if (importUserTaskWorkerDataEnabled == null) {
      importUserTaskWorkerDataEnabled = configJsonContext.read(
        ConfigurationServiceConstants.IMPORT_USER_TASK_WORKER_DATA,
        Boolean.class
      );
    }
    return importUserTaskWorkerDataEnabled;
  }

  public Boolean getSkipDataAfterNestedDocLimitReached() {
    if (skipDataAfterNestedDocLimitReached == null) {
      skipDataAfterNestedDocLimitReached = configJsonContext.read(
        ConfigurationServiceConstants.IMPORT_SKIP_DATA_AFTER_NESTED_DOC_LIMIT_REACHED,
        Boolean.class
      );
    }
    return skipDataAfterNestedDocLimitReached;
  }

  @JsonIgnore
  public boolean isImportUserTaskWorkerDataEnabled() {
    return getImportUserTaskWorkerDataEnabled();
  }

  public String getNotificationEmailAddress() {
    if (notificationEmailAddress == null) {
      notificationEmailAddress = configJsonContext.read(ConfigurationServiceConstants.EMAIL_ADDRESS);
    }
    return notificationEmailAddress;
  }

  public String getNotificationEmailHostname() {
    if (notificationEmailHostname == null) {
      notificationEmailHostname = configJsonContext.read(ConfigurationServiceConstants.EMAIL_HOSTNAME);
    }
    return notificationEmailHostname;
  }

  public Integer getNotificationEmailPort() {
    if (notificationEmailPort == null) {
      notificationEmailPort = configJsonContext.read(ConfigurationServiceConstants.EMAIL_PORT, Integer.class);
    }
    return notificationEmailPort;
  }

  public String getNotificationEmailCompanyBranding() {
    if (notificationEmailCompanyBranding == null) {
      notificationEmailCompanyBranding = configJsonContext.read(ConfigurationServiceConstants.EMAIL_BRANDING, String.class);
    }
    return notificationEmailCompanyBranding;
  }

  public Map<String, WebhookConfiguration> getConfiguredWebhooks() {
    if (configuredWebhooks == null) {
      configuredWebhooks = configJsonContext.read(
        ConfigurationServiceConstants.CONFIGURED_WEBHOOKS,
        WEBHOOKS_MAP_TYPEREF
      );
      if (configuredWebhooks == null) {
        configuredWebhooks = Collections.emptyMap();
      }
    }
    return configuredWebhooks;
  }

  public String getDigestCronTrigger() {
    if (digestCronTrigger == null) {
      digestCronTrigger = configJsonContext.read(ConfigurationServiceConstants.DIGEST_CRON_TRIGGER, String.class);
    }
    return digestCronTrigger;
  }

  public EntityConfiguration getEntityConfiguration() {
    if (entityConfiguration == null) {
      entityConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.ENTITY_CONFIGURATION,
        EntityConfiguration.class
      );
    }
    return entityConfiguration;
  }

  public CsvConfiguration getCsvConfiguration() {
    if (csvConfiguration == null) {
      csvConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.CSV_CONFIGURATION,
        CsvConfiguration.class
      );
    }
    return csvConfiguration;
  }

  public String getElasticsearchSecurityUsername() {
    if (elasticsearchSecurityUsername == null) {
      elasticsearchSecurityUsername =
        configJsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_SECURITY_USERNAME);
    }
    return elasticsearchSecurityUsername;
  }

  public String getElasticsearchSecurityPassword() {
    if (elasticsearchSecurityPassword == null) {
      elasticsearchSecurityPassword =
        configJsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_SECURITY_PASSWORD);
    }
    return elasticsearchSecurityPassword;
  }

  public Boolean getElasticsearchSecuritySSLEnabled() {
    if (elasticsearchSecuritySSLEnabled == null) {
      elasticsearchSecuritySSLEnabled =
        configJsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_SECURITY_SSL_ENABLED, Boolean.class);
    }
    return elasticsearchSecuritySSLEnabled;
  }

  public Boolean getElasticsearchSecuritySslSelfSigned() {
    if (elasticsearchSecuritySslSelfSigned == null && getElasticsearchSecuritySSLEnabled()) {
      elasticsearchSecuritySslSelfSigned =
        configJsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_SECURITY_SSL_SELF_SIGNED, Boolean.class);
    }
    return elasticsearchSecuritySslSelfSigned;
  }

  public String getElasticsearchSecuritySSLCertificate() {
    if (elasticsearchSecuritySSLCertificate == null && getElasticsearchSecuritySSLEnabled()) {
      elasticsearchSecuritySSLCertificate = configJsonContext.read(
        ConfigurationServiceConstants.ELASTICSEARCH_SECURITY_SSL_CERTIFICATE
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
        ELASTICSEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES, LIST_OF_STRINGS_TYPE_REF
      );
      elasticsearchSecuritySSLCertificateAuthorities = authoritiesAsList.stream()
        .map(a -> resolvePathAsAbsoluteUrl(a).getPath())
        .collect(Collectors.toList());
    }
    return Optional.ofNullable(elasticsearchSecuritySSLCertificateAuthorities).orElse(new ArrayList<>());
  }

  public CleanupConfiguration getCleanupServiceConfiguration() {
    if (cleanupServiceConfiguration == null) {
      cleanupServiceConfiguration = configJsonContext.read(
        ConfigurationServiceConstants.HISTORY_CLEANUP,
        CleanupConfiguration.class
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

  public UserIdentityCacheConfiguration getUserIdentityCacheConfiguration() {
    if (userIdentityCacheConfiguration == null) {
      userIdentityCacheConfiguration = configJsonContext.read(
        IDENTITY_SYNC_CONFIGURATION,
        UserIdentityCacheConfiguration.class
      );
    }
    return userIdentityCacheConfiguration;
  }

  public UserTaskIdentityCacheConfiguration getUserTaskIdentityCacheConfiguration() {
    if (userTaskIdentityCacheConfiguration == null) {
      userTaskIdentityCacheConfiguration = configJsonContext.read(
        IMPORT_USER_TASK_IDENTITY_META_DATA, UserTaskIdentityCacheConfiguration.class
      );
    }
    return userTaskIdentityCacheConfiguration;
  }

  @JsonIgnore
  public IndexRolloverConfiguration getEventIndexRolloverConfiguration() {
    return getEventBasedProcessConfiguration().getEventIndexRollover();
  }

  public EventBasedProcessConfiguration getEventBasedProcessConfiguration() {
    if (eventBasedProcessConfiguration == null) {
      eventBasedProcessConfiguration = configJsonContext.read(
        EVENT_BASED_PROCESS_CONFIGURATION,
        EventBasedProcessConfiguration.class
      );
    }
    return eventBasedProcessConfiguration;
  }

  @JsonIgnore
  public EventImportConfiguration getEventImportConfiguration() {
    return getEventBasedProcessConfiguration().getEventImport();
  }

  @JsonIgnore
  public EventIngestionConfiguration getEventIngestionConfiguration() {
    return getEventBasedProcessConfiguration().getEventIngestion();
  }

  @JsonIgnore
  public List<String> getEventBasedProcessAccessUserIds() {
    return getEventBasedProcessConfiguration().getAuthorizedUserIds();
  }

  @JsonIgnore
  public List<String> getEventBasedProcessAccessGroupIds() {
    return getEventBasedProcessConfiguration().getAuthorizedGroupIds();
  }

  public OptimizeApiConfiguration getOptimizeApiConfiguration() {
    if (optimizeApiConfiguration == null) {
      optimizeApiConfiguration = configJsonContext.read(
        OPTIMIZE_API_CONFIGURATION,
        OptimizeApiConfiguration.class
      );
    }
    return optimizeApiConfiguration;
  }

  public TelemetryConfiguration getTelemetryConfiguration() {
    if (telemetryConfiguration == null) {
      telemetryConfiguration = configJsonContext.read(
        TELEMETRY_CONFIGURATION,
        TelemetryConfiguration.class
      );
    }
    return telemetryConfiguration;
  }

  public ExternalVariableConfiguration getExternalVariableConfiguration() {
    if (externalVariableConfiguration == null) {
      externalVariableConfiguration = configJsonContext.read(
        EXTERNAL_VARIABLE_CONFIGURATION,
        ExternalVariableConfiguration.class
      );
    }
    return externalVariableConfiguration;
  }

  @JsonIgnore
  public VariableIngestionConfiguration getVariableIngestionConfiguration() {
    return getExternalVariableConfiguration().getVariableIngestion();
  }

  @JsonIgnore
  public IndexRolloverConfiguration getVariableIndexRolloverConfiguration() {
    return getExternalVariableConfiguration().getVariableIndexRollover();
  }

  public GlobalCacheConfiguration getCaches() {
    if (caches == null) {
      caches = configJsonContext.read(CACHES_CONFIGURATION, GlobalCacheConfiguration.class);
    }
    return caches;
  }

  public AnalyticsConfiguration getAnalytics() {
    if (analytics == null) {
      analytics = configJsonContext.read(ANALYTICS_CONFIGURATION, AnalyticsConfiguration.class);
    }
    return analytics;
  }

  public OnboardingConfiguration getOnboarding() {
    if (onboarding == null) {
      onboarding = configJsonContext.read(ONBOARDING_CONFIGURATION, OnboardingConfiguration.class);
    }
    return onboarding;
  }

}
