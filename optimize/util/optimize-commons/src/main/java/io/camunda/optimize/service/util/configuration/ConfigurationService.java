/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationParser.parseConfigFromLocations;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ANALYTICS_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.AVAILABLE_LOCALES;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CACHES_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_DATABASE_PROPERTY;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.EXTERNAL_VARIABLE_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.FALLBACK_LOCALE;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IDENTITY_SYNC_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IMPORT_USER_TASK_IDENTITY_META_DATA;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.M2M_CLIENT_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ONBOARDING_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPENSEARCH_DATABASE_PROPERTY;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPTIMIZE_API_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.PANEL_NOTIFICATION_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.TELEMETRY_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.UI_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.optimizeModeProfiles;
import static io.camunda.optimize.service.util.configuration.ConfigurationUtil.ensureGreaterThanZero;
import static io.camunda.optimize.service.util.configuration.ConfigurationUtil.getLocationsAsInputStream;
import static io.camunda.optimize.util.SuppressionConstants.OPTIONAL_ASSIGNED_TO_NULL;
import static io.camunda.optimize.util.SuppressionConstants.OPTIONAL_FIELD_OR_PARAM;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import io.camunda.optimize.dto.optimize.SchedulerConfig;
import io.camunda.optimize.dto.optimize.ZeebeConfigDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.configuration.analytics.AnalyticsConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.engine.EngineAuthenticationConfiguration;
import io.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import io.camunda.optimize.service.util.configuration.engine.UserIdentityCacheConfiguration;
import io.camunda.optimize.service.util.configuration.engine.UserTaskIdentityCacheConfiguration;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.SecurityConfiguration;
import io.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import io.camunda.optimize.service.util.configuration.users.UsersConfiguration;
import io.camunda.optimize.util.SuppressionConstants;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.core.env.Environment;

public class ConfigurationService {

  private static final String ERROR_NO_ENGINE_WITH_ALIAS = "No Engine configured with alias ";

  // @formatter:off
  private static final TypeRef<HashMap<String, EngineConfiguration>> ENGINES_MAP_TYPEREF =
      new TypeRef<>() {};
  private static final TypeRef<List<String>> LIST_OF_STRINGS_TYPE_REF = new TypeRef<>() {};
  private static final TypeRef<HashMap<String, WebhookConfiguration>> WEBHOOKS_MAP_TYPEREF =
      new TypeRef<>() {};
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ConfigurationService.class);
  // @formatter:on
  // job executor settings
  protected Integer jobExecutorQueueSize;
  protected Integer jobExecutorThreadCount;
  private ElasticSearchConfiguration elasticSearchConfiguration;
  private OpenSearchConfiguration openSearchConfiguration;
  private ReadContext configJsonContext;
  private SecurityConfiguration securityConfiguration;
  private UsersConfiguration usersConfiguration;
  private Map<String, EngineConfiguration> configuredEngines;
  private ZeebeConfiguration configuredZeebe;
  private String engineDateFormat;
  private Long initialBackoff;
  private Long maximumBackoff;
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
  private String containerHost;
  private String contextPath;
  private String containerKeystorePassword;
  private String containerKeystoreLocation;
  private Boolean containerEnableSniCheck;
  private Integer containerHttpsPort;
  private Integer actuatorPort;
  private Boolean containerHttp2Enabled;

  // we use optional field here in order to allow restoring defaults with BeanUtils.copyProperties
  // if only the getter is of type Optional the value won't get reset properly
  @SuppressWarnings(OPTIONAL_FIELD_OR_PARAM)
  private Optional<String> containerAccessUrl;

  private Integer maxRequestHeaderSizeInBytes;
  private Integer maxResponseHeaderSizeInBytes;

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
  private Boolean notificationEmailCheckServerIdentity;
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
  private TelemetryConfiguration telemetryConfiguration;
  private ExternalVariableConfiguration externalVariableConfiguration;
  private GlobalCacheConfiguration caches;
  private AnalyticsConfiguration analytics;
  private OptimizeApiConfiguration optimizeApiConfiguration;
  private OnboardingConfiguration onboarding;
  private PanelNotificationConfiguration panelNotificationConfiguration;
  private M2mAuth0ClientConfiguration m2mAuth0ClientConfiguration;
  private Boolean multiTenancyEnabled;

  public ConfigurationService(
      final String[] configLocations, final ConfigurationValidator configurationValidator) {
    final List<InputStream> configStreams = getLocationsAsInputStream(configLocations);
    configJsonContext =
        parseConfigFromLocations(configStreams)
            .orElseThrow(
                () ->
                    new OptimizeConfigurationException(
                        "No single configuration source could be read"));
    Optional.ofNullable(configurationValidator).ifPresent(validator -> validator.validate(this));
  }

  @JsonCreator
  public static ConfigurationService createDefault() {
    return ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  public static OptimizeProfile getOptimizeProfile(final Environment environment) {
    final List<OptimizeProfile> specifiedProfiles =
        Arrays.stream(environment.getActiveProfiles())
            .filter(optimizeModeProfiles::contains)
            .map(OptimizeProfile::toProfile)
            .toList();
    if (specifiedProfiles.size() > 1) {
      throw new OptimizeConfigurationException("Cannot configure more than one Optimize profile");
    } else if (specifiedProfiles.isEmpty()) {
      return OptimizeProfile.CCSM;
    } else {
      return specifiedProfiles.get(0);
    }
  }

  public static DatabaseType getDatabaseType(final Environment environment) {
    final String configuredProperty =
        environment.getProperty(CAMUNDA_OPTIMIZE_DATABASE, ELASTICSEARCH_DATABASE_PROPERTY);
    return convertToDatabaseProperty(configuredProperty);
  }

  public static DatabaseType convertToDatabaseProperty(final String configuredProperty) {
    if (configuredProperty == null) {
      throw new OptimizeConfigurationException("configuredProperty cannot be null");
    }

    if (configuredProperty.equalsIgnoreCase(ELASTICSEARCH_DATABASE_PROPERTY)) {
      return DatabaseType.ELASTICSEARCH;
    } else if (configuredProperty.equalsIgnoreCase(OPENSEARCH_DATABASE_PROPERTY)) {
      return DatabaseType.OPENSEARCH;
    } else {
      final String reason =
          String.format(
              "Cannot start Optimize. Invalid database configured %s", configuredProperty);
      log.error(reason);
      throw new OptimizeConfigurationException(reason);
    }
  }

  public ElasticSearchConfiguration getElasticSearchConfiguration() {
    if (elasticSearchConfiguration == null) {
      elasticSearchConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.ELASTICSEARCH, ElasticSearchConfiguration.class);
    }
    return elasticSearchConfiguration;
  }

  public OpenSearchConfiguration getOpenSearchConfiguration() {
    if (openSearchConfiguration == null) {
      openSearchConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.OPENSEARCH, OpenSearchConfiguration.class);
    }
    return openSearchConfiguration;
  }

  ReadContext getConfigJsonContext() {
    return configJsonContext;
  }

  public Map<String, EngineConfiguration> getConfiguredEngines() {
    if (configuredEngines == null) {
      configuredEngines =
          configJsonContext.read(
              ConfigurationServiceConstants.CONFIGURED_ENGINES, ENGINES_MAP_TYPEREF);
    }
    return configuredEngines;
  }

  public ZeebeConfiguration getConfiguredZeebe() {
    if (configuredZeebe == null) {
      configuredZeebe =
          configJsonContext.read(
              ConfigurationServiceConstants.CONFIGURED_ZEEBE, ZeebeConfiguration.class);
    }
    return configuredZeebe;
  }

  public SecurityConfiguration getSecurityConfiguration() {
    if (securityConfiguration == null) {
      securityConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.SECURITY, SecurityConfiguration.class);
    }
    return securityConfiguration;
  }

  public UsersConfiguration getUsersConfiguration() {
    if (usersConfiguration == null) {
      usersConfiguration =
          configJsonContext.read(ConfigurationServiceConstants.USERS, UsersConfiguration.class);
    }
    return usersConfiguration;
  }

  @JsonIgnore
  public AuthConfiguration getAuthConfiguration() {
    return getSecurityConfiguration().getAuth();
  }

  public String getEngineDateFormat() {
    if (engineDateFormat == null) {
      engineDateFormat = configJsonContext.read(ConfigurationServiceConstants.ENGINE_DATE_FORMAT);
    }
    return engineDateFormat;
  }

  public int getImportIndexAutoStorageIntervalInSec() {
    if (importIndexAutoStorageIntervalInSec == null) {
      importIndexAutoStorageIntervalInSec =
          configJsonContext.read(
              ConfigurationServiceConstants.IMPORT_INDEX_AUTO_STORAGE_INTERVAL, Integer.class);
    }
    return importIndexAutoStorageIntervalInSec;
  }

  public long getInitialBackoff() {
    if (initialBackoff == null) {
      initialBackoff =
          configJsonContext.read(
              ConfigurationServiceConstants.INITIAL_BACKOFF_INTERVAL, Long.class);
    }
    return initialBackoff;
  }

  public long getMaximumBackoff() {
    if (maximumBackoff == null) {
      maximumBackoff =
          configJsonContext.read(ConfigurationServiceConstants.MAXIMUM_BACK_OFF, Long.class);
    }
    return maximumBackoff;
  }

  public int getEngineConnectTimeout() {
    if (engineConnectTimeout == null) {
      engineConnectTimeout =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_CONNECT_TIMEOUT, Integer.class);
    }
    return engineConnectTimeout;
  }

  public int getEngineReadTimeout() {
    if (engineReadTimeout == null) {
      engineReadTimeout =
          configJsonContext.read(ConfigurationServiceConstants.ENGINE_READ_TIMEOUT, Integer.class);
    }
    return engineReadTimeout;
  }

  public int getCurrentTimeBackoffMilliseconds() {
    if (currentTimeBackoffMilliseconds == null) {
      currentTimeBackoffMilliseconds =
          configJsonContext.read(
              ConfigurationServiceConstants.IMPORT_CURRENT_TIME_BACKOFF_MILLISECONDS,
              Integer.class);
    }
    return currentTimeBackoffMilliseconds;
  }

  public int getEngineImportProcessInstanceMaxPageSize() {
    if (engineImportProcessInstanceMaxPageSize == null) {
      engineImportProcessInstanceMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportProcessInstanceMaxPageSize);
    return engineImportProcessInstanceMaxPageSize;
  }

  public int getEngineImportVariableInstanceMaxPageSize() {
    if (engineImportVariableInstanceMaxPageSize == null) {
      engineImportVariableInstanceMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportVariableInstanceMaxPageSize);
    return engineImportVariableInstanceMaxPageSize;
  }

  public boolean getEngineImportVariableIncludeObjectVariableValue() {
    if (engineImportVariableIncludeObjectVariableValue == null) {
      engineImportVariableIncludeObjectVariableValue =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_VARIABLE_INCLUDE_OBJECT_VARIABLE_VALUE,
              Boolean.class);
    }
    return engineImportVariableIncludeObjectVariableValue;
  }

  public int getEngineImportProcessDefinitionXmlMaxPageSize() {
    if (engineImportProcessDefinitionXmlMaxPageSize == null) {
      engineImportProcessDefinitionXmlMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportProcessDefinitionXmlMaxPageSize);
    return engineImportProcessDefinitionXmlMaxPageSize;
  }

  public int getEngineImportProcessDefinitionMaxPageSize() {
    if (engineImportProcessDefinitionMaxPageSize == null) {
      engineImportProcessDefinitionMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportProcessDefinitionMaxPageSize);
    return engineImportProcessDefinitionMaxPageSize;
  }

  public boolean getSharingEnabled() {
    if (sharingEnabled == null) {
      sharingEnabled =
          configJsonContext.read(ConfigurationServiceConstants.SHARING_ENABLED, Boolean.class);
    }
    return Optional.ofNullable(sharingEnabled).orElse(false);
  }

  public int getEngineImportDecisionDefinitionXmlMaxPageSize() {
    if (engineImportDecisionDefinitionXmlMaxPageSize == null) {
      engineImportDecisionDefinitionXmlMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_DEFINITION_XML_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportDecisionDefinitionXmlMaxPageSize);
    return engineImportDecisionDefinitionXmlMaxPageSize;
  }

  public int getEngineImportDecisionDefinitionMaxPageSize() {
    if (engineImportDecisionDefinitionMaxPageSize == null) {
      engineImportDecisionDefinitionMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_DEFINITION_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportDecisionDefinitionMaxPageSize);
    return engineImportDecisionDefinitionMaxPageSize;
  }

  public boolean getCustomerOnboardingImport() {
    if (customerOnboarding == null) {
      customerOnboarding =
          configJsonContext.read(
              ConfigurationServiceConstants.CUSTOMER_ONBOARDING_DATA, Boolean.class);
    }
    return customerOnboarding;
  }

  public void setCustomerOnboardingImport(final boolean isActive) {
    customerOnboarding = isActive;
  }

  public int getEngineImportDecisionInstanceMaxPageSize() {
    if (engineImportDecisionInstanceMaxPageSize == null) {
      engineImportDecisionInstanceMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_DECISION_INSTANCE_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportDecisionInstanceMaxPageSize);
    return engineImportDecisionInstanceMaxPageSize;
  }

  public int getEngineImportTenantMaxPageSize() {
    if (engineImportTenantMaxPageSize == null) {
      engineImportTenantMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_TENANT_MAX_PAGE_SIZE, Integer.class);
    }
    ensureGreaterThanZero(engineImportTenantMaxPageSize);
    return engineImportTenantMaxPageSize;
  }

  public int getEngineImportGroupMaxPageSize() {
    if (engineImportGroupMaxPageSize == null) {
      engineImportGroupMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_GROUP_MAX_PAGE_SIZE, Integer.class);
    }
    ensureGreaterThanZero(engineImportGroupMaxPageSize);
    return engineImportGroupMaxPageSize;
  }

  public int getEngineImportAuthorizationMaxPageSize() {
    if (engineImportAuthorizationMaxPageSize == null) {
      engineImportAuthorizationMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_AUTHORIZATION_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportAuthorizationMaxPageSize);
    return engineImportAuthorizationMaxPageSize;
  }

  public int getEngineImportActivityInstanceMaxPageSize() {
    if (engineImportActivityInstanceMaxPageSize == null) {
      engineImportActivityInstanceMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportActivityInstanceMaxPageSize);
    return engineImportActivityInstanceMaxPageSize;
  }

  public int getEngineImportIncidentMaxPageSize() {
    if (engineImportIncidentMaxPageSize == null) {
      engineImportIncidentMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_INCIDENT_MAX_PAGE_SIZE, Integer.class);
    }
    ensureGreaterThanZero(engineImportIncidentMaxPageSize);
    return engineImportIncidentMaxPageSize;
  }

  public int getEngineImportUserTaskInstanceMaxPageSize() {
    if (engineImportUserTaskInstanceMaxPageSize == null) {
      engineImportUserTaskInstanceMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_USER_TASK_INSTANCE_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportUserTaskInstanceMaxPageSize);
    return engineImportUserTaskInstanceMaxPageSize;
  }

  public int getEngineImportIdentityLinkLogsMaxPageSize() {
    if (engineImportIdentityLinkLogsMaxPageSize == null) {
      engineImportIdentityLinkLogsMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_IDENTITY_LING_LOG_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportIdentityLinkLogsMaxPageSize);
    return engineImportIdentityLinkLogsMaxPageSize;
  }

  public int getEngineImportUserOperationLogsMaxPageSize() {
    if (engineImportUserOperationLogsMaxPageSize == null) {
      engineImportUserOperationLogsMaxPageSize =
          configJsonContext.read(
              ConfigurationServiceConstants.ENGINE_IMPORT_USER_OPERATION_LOG_MAX_PAGE_SIZE,
              Integer.class);
    }
    ensureGreaterThanZero(engineImportUserOperationLogsMaxPageSize);
    return engineImportUserOperationLogsMaxPageSize;
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
      containerKeystorePassword =
          configJsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_PASSWORD);
    }
    return containerKeystorePassword;
  }

  @SuppressWarnings(OPTIONAL_ASSIGNED_TO_NULL)
  public Optional<String> getContainerAccessUrl() {
    if (containerAccessUrl == null) {
      containerAccessUrl =
          Optional.ofNullable(
              configJsonContext.read(ConfigurationServiceConstants.CONTAINER_ACCESS_URL));
    }
    return containerAccessUrl;
  }

  // Note: special setter for Optional field value, see note on field why the field is Optional
  public void setContainerAccessUrlValue(final String containerAccessUrl) {
    this.containerAccessUrl = Optional.ofNullable(containerAccessUrl);
  }

  public Integer getMaxRequestHeaderSizeInBytes() {
    if (maxRequestHeaderSizeInBytes == null) {
      maxRequestHeaderSizeInBytes =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_MAX_REQUEST_HEADER_IN_BYTES, Integer.class);
    }
    return maxRequestHeaderSizeInBytes;
  }

  public Integer getMaxResponseHeaderSizeInBytes() {
    if (maxResponseHeaderSizeInBytes == null) {
      maxResponseHeaderSizeInBytes =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_MAX_RESPONSE_HEADER_IN_BYTES, Integer.class);
    }
    return maxResponseHeaderSizeInBytes;
  }

  public String getContainerKeystoreLocation() {
    if (containerKeystoreLocation == null) {
      containerKeystoreLocation =
          configJsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_LOCATION);
      // we need external form here for the path to work if the keystore is inside the jar (default)
      containerKeystoreLocation =
          ConfigurationUtil.resolvePathAsAbsoluteUrl(containerKeystoreLocation).toExternalForm();
    }
    return containerKeystoreLocation;
  }

  public Boolean getContainerEnableSniCheck() {
    if (containerEnableSniCheck == null) {
      containerEnableSniCheck =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_ENABLE_SNI_CHECK, Boolean.class);
    }
    return containerEnableSniCheck;
  }

  public Boolean getContainerHttp2Enabled() {
    if (containerHttp2Enabled == null) {
      containerHttp2Enabled =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_HTTP2_ENABLED, Boolean.class);
    }
    return containerHttp2Enabled;
  }

  public Integer getContainerHttpsPort() {
    if (containerHttpsPort == null) {
      containerHttpsPort =
          configJsonContext.read(ConfigurationServiceConstants.CONTAINER_HTTPS_PORT, Integer.class);
      if (containerHttpsPort == null) {
        throw new OptimizeConfigurationException(
            "Optimize container https port is not allowed to be null!");
      }
    }
    return containerHttpsPort;
  }

  public Integer getActuatorPort() {
    if (actuatorPort == null) {
      actuatorPort =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_ACTUATOR_PORT, Integer.class);
      if (actuatorPort == null) {
        throw new OptimizeConfigurationException(
            "Optimize actuator port is not allowed to be null!");
      }
    }
    return actuatorPort;
  }

  public Optional<Integer> getContainerHttpPort() {
    // containerHttpPort is optional so we can adjust
    // it during tests. Thus it is still null initially
    // and need to be checked therefore.
    //noinspection OptionalAssignedToNull
    if (containerHttpPort == null) {
      containerHttpPort =
          Optional.ofNullable(
              configJsonContext.read(
                  ConfigurationServiceConstants.CONTAINER_HTTP_PORT, Integer.class));
    }
    return containerHttpPort;
  }

  // Note: special setter for Optional field value, see note on field why the field is Optional
  @SuppressWarnings(SuppressionConstants.UNUSED)
  public void setContainerHttpPortValue(final Integer containerHttpPort) {
    this.containerHttpPort = Optional.ofNullable(containerHttpPort);
  }

  public int getMaxStatusConnections() {
    if (maxStatusConnections == null) {
      maxStatusConnections =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_STATUS_MAX_CONNECTIONS, Integer.class);
    }
    return maxStatusConnections;
  }

  public Optional<String> getEngineDefaultTenantIdOfCustomEngine(final String engineAlias) {
    return getEngineConfiguration(engineAlias)
        .map(EngineConfiguration::getDefaultTenantId)
        .orElseThrow(
            () -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public List<String> getExcludedTenants(final String engineAlias) {
    return getEngineConfiguration(engineAlias)
        .map(EngineConfiguration::getExcludedTenants)
        .orElseThrow(
            () -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public String getEngineRestApiEndpointOfCustomEngine(final String engineAlias) {
    return getEngineRestApiEndpoint(engineAlias) + "/engine/" + getEngineName(engineAlias);
  }

  public String getDefaultEngineAuthenticationUser(final String engineAlias) {
    return getEngineConfiguration(engineAlias)
        .map(EngineConfiguration::getAuthentication)
        .map(EngineAuthenticationConfiguration::getUser)
        .orElseThrow(
            () -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public String getDefaultEngineAuthenticationPassword(final String engineAlias) {
    return getEngineConfiguration(engineAlias)
        .map(EngineConfiguration::getAuthentication)
        .map(EngineAuthenticationConfiguration::getPassword)
        .orElseThrow(
            () -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  /**
   * This method is mostly for internal usage. All API invocations should rely on {@link
   * #getEngineRestApiEndpointOfCustomEngine(String)}
   *
   * @param engineAlias - an alias of configured engine
   * @return <b>raw</b> REST endpoint, without engine suffix
   */
  private String getEngineRestApiEndpoint(final String engineAlias) {
    return getEngineConfiguration(engineAlias)
        .map(EngineConfiguration::getRest)
        .orElseThrow(
            () -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public String getEngineName(final String engineAlias) {
    return getEngineConfiguration(engineAlias)
        .map(EngineConfiguration::getName)
        .orElseThrow(
            () -> new OptimizeConfigurationException(ERROR_NO_ENGINE_WITH_ALIAS + engineAlias));
  }

  public boolean isImportEnabled(final SchedulerConfig dataSourceDto) {
    if (dataSourceDto instanceof final EngineDataSourceDto engineSource) {
      return getEngineConfiguration(engineSource.getName())
          .map(EngineConfiguration::isImportEnabled)
          .orElseThrow(
              () ->
                  new OptimizeConfigurationException(
                      ERROR_NO_ENGINE_WITH_ALIAS + engineSource.getName()));
    } else if (dataSourceDto instanceof ZeebeConfigDto) {
      return getConfiguredZeebe().isEnabled();
    } else if (dataSourceDto instanceof IngestedDataSourceDto) {
      return getExternalVariableConfiguration().getImportConfiguration().isEnabled();
    }
    throw new OptimizeConfigurationException("Invalid data import source");
  }

  public Optional<EngineConfiguration> getEngineConfiguration(final String engineAlias) {
    return Optional.ofNullable(getConfiguredEngines().get(engineAlias));
  }

  public Properties getQuartzProperties() {
    if (quartzProperties == null) {
      quartzProperties = new Properties();
      quartzProperties.put(
          "org.quartz.jobStore.class",
          configJsonContext.read(ConfigurationServiceConstants.QUARTZ_JOB_STORE_CLASS));
    }
    return quartzProperties;
  }

  public boolean getEmailEnabled() {
    if (emailEnabled == null) {
      emailEnabled =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_ENABLED, Boolean.class);
    }
    return emailEnabled;
  }

  public EmailAuthenticationConfiguration getEmailAuthenticationConfiguration() {
    if (emailAuthenticationConfiguration == null) {
      emailAuthenticationConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.EMAIL_AUTHENTICATION,
              EmailAuthenticationConfiguration.class);
    }
    return emailAuthenticationConfiguration;
  }

  private Boolean getImportDmnDataEnabled() {
    if (importDmnDataEnabled == null) {
      importDmnDataEnabled =
          configJsonContext.read(ConfigurationServiceConstants.IMPORT_DMN_DATA, Boolean.class);
    }
    return importDmnDataEnabled;
  }

  @JsonIgnore
  public boolean isImportDmnDataEnabled() {
    return getImportDmnDataEnabled();
  }

  private Boolean getImportUserTaskWorkerDataEnabled() {
    if (importUserTaskWorkerDataEnabled == null) {
      importUserTaskWorkerDataEnabled =
          configJsonContext.read(
              ConfigurationServiceConstants.IMPORT_USER_TASK_WORKER_DATA, Boolean.class);
    }
    return importUserTaskWorkerDataEnabled;
  }

  public Boolean getSkipDataAfterNestedDocLimitReached() {
    if (skipDataAfterNestedDocLimitReached == null) {
      skipDataAfterNestedDocLimitReached =
          configJsonContext.read(
              ConfigurationServiceConstants.IMPORT_SKIP_DATA_AFTER_NESTED_DOC_LIMIT_REACHED,
              Boolean.class);
    }
    return skipDataAfterNestedDocLimitReached;
  }

  @JsonIgnore
  public boolean isImportUserTaskWorkerDataEnabled() {
    return getImportUserTaskWorkerDataEnabled();
  }

  public String getNotificationEmailAddress() {
    if (notificationEmailAddress == null) {
      notificationEmailAddress =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_ADDRESS);
    }
    return notificationEmailAddress;
  }

  public String getNotificationEmailHostname() {
    if (notificationEmailHostname == null) {
      notificationEmailHostname =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_HOSTNAME);
    }
    return notificationEmailHostname;
  }

  public Integer getNotificationEmailPort() {
    if (notificationEmailPort == null) {
      notificationEmailPort =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_PORT, Integer.class);
    }
    return notificationEmailPort;
  }

  public Boolean getNotificationEmailCheckServerIdentity() {
    return Optional.ofNullable(notificationEmailCheckServerIdentity)
        .orElse(
            configJsonContext.read(
                ConfigurationServiceConstants.CHECK_SERVER_IDENTITY, Boolean.class));
  }

  public String getNotificationEmailCompanyBranding() {
    if (notificationEmailCompanyBranding == null) {
      notificationEmailCompanyBranding =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_BRANDING, String.class);
    }
    return notificationEmailCompanyBranding;
  }

  public Map<String, WebhookConfiguration> getConfiguredWebhooks() {
    if (configuredWebhooks == null) {
      configuredWebhooks =
          configJsonContext.read(
              ConfigurationServiceConstants.CONFIGURED_WEBHOOKS, WEBHOOKS_MAP_TYPEREF);
      if (configuredWebhooks == null) {
        configuredWebhooks = Collections.emptyMap();
      }
    }
    return configuredWebhooks;
  }

  public String getDigestCronTrigger() {
    if (digestCronTrigger == null) {
      digestCronTrigger =
          configJsonContext.read(ConfigurationServiceConstants.DIGEST_CRON_TRIGGER, String.class);
    }
    return digestCronTrigger;
  }

  public EntityConfiguration getEntityConfiguration() {
    if (entityConfiguration == null) {
      entityConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.ENTITY_CONFIGURATION, EntityConfiguration.class);
    }
    return entityConfiguration;
  }

  public CsvConfiguration getCsvConfiguration() {
    if (csvConfiguration == null) {
      csvConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.CSV_CONFIGURATION, CsvConfiguration.class);
    }
    return csvConfiguration;
  }

  public CleanupConfiguration getCleanupServiceConfiguration() {
    if (cleanupServiceConfiguration == null) {
      cleanupServiceConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.HISTORY_CLEANUP, CleanupConfiguration.class);
      cleanupServiceConfiguration.validate();
    }
    return cleanupServiceConfiguration;
  }

  public List<String> getAvailableLocales() {
    if (availableLocales == null) {
      availableLocales = configJsonContext.read(AVAILABLE_LOCALES, LIST_OF_STRINGS_TYPE_REF);
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
      uiConfiguration.validate();
    }
    return uiConfiguration;
  }

  public UserIdentityCacheConfiguration getUserIdentityCacheConfiguration() {
    if (userIdentityCacheConfiguration == null) {
      userIdentityCacheConfiguration =
          configJsonContext.read(IDENTITY_SYNC_CONFIGURATION, UserIdentityCacheConfiguration.class);
    }
    return userIdentityCacheConfiguration;
  }

  public UserTaskIdentityCacheConfiguration getUserTaskIdentityCacheConfiguration() {
    if (userTaskIdentityCacheConfiguration == null) {
      userTaskIdentityCacheConfiguration =
          configJsonContext.read(
              IMPORT_USER_TASK_IDENTITY_META_DATA, UserTaskIdentityCacheConfiguration.class);
    }
    return userTaskIdentityCacheConfiguration;
  }

  public OptimizeApiConfiguration getOptimizeApiConfiguration() {
    if (optimizeApiConfiguration == null) {
      optimizeApiConfiguration =
          configJsonContext.read(OPTIMIZE_API_CONFIGURATION, OptimizeApiConfiguration.class);
    }
    return optimizeApiConfiguration;
  }

  public TelemetryConfiguration getTelemetryConfiguration() {
    if (telemetryConfiguration == null) {
      telemetryConfiguration =
          configJsonContext.read(TELEMETRY_CONFIGURATION, TelemetryConfiguration.class);
    }
    return telemetryConfiguration;
  }

  public ExternalVariableConfiguration getExternalVariableConfiguration() {
    if (externalVariableConfiguration == null) {
      externalVariableConfiguration =
          configJsonContext.read(
              EXTERNAL_VARIABLE_CONFIGURATION, ExternalVariableConfiguration.class);
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

  public PanelNotificationConfiguration getPanelNotificationConfiguration() {
    if (panelNotificationConfiguration == null) {
      panelNotificationConfiguration =
          configJsonContext.read(
              PANEL_NOTIFICATION_CONFIGURATION, PanelNotificationConfiguration.class);
    }
    return panelNotificationConfiguration;
  }

  public M2mAuth0ClientConfiguration getM2mAuth0ClientConfiguration() {
    if (m2mAuth0ClientConfiguration == null) {
      m2mAuth0ClientConfiguration =
          configJsonContext.read(M2M_CLIENT_CONFIGURATION, M2mAuth0ClientConfiguration.class);
    }
    return m2mAuth0ClientConfiguration;
  }

  public boolean isMultiTenancyEnabled() {
    if (multiTenancyEnabled == null) {
      multiTenancyEnabled =
          configJsonContext.read(ConfigurationServiceConstants.MULTITENANCY_ENABLED, Boolean.class);
    }
    return multiTenancyEnabled;
  }

  public Integer getJobExecutorQueueSize() {
    if (jobExecutorQueueSize == null) {
      jobExecutorQueueSize =
          configJsonContext.read(
              ConfigurationServiceConstants.DATABASE_MAX_JOB_QUEUE_SIZE, Integer.class);
    }
    return jobExecutorQueueSize;
  }

  public Integer getJobExecutorThreadCount() {
    if (jobExecutorThreadCount == null) {
      jobExecutorThreadCount =
          configJsonContext.read(
              ConfigurationServiceConstants.DATABASE_IMPORT_EXECUTOR_THREAD_COUNT, Integer.class);
    }
    return jobExecutorThreadCount;
  }

  public void setJobExecutorQueueSize(Integer jobExecutorQueueSize) {
    this.jobExecutorQueueSize = jobExecutorQueueSize;
  }

  public void setJobExecutorThreadCount(Integer jobExecutorThreadCount) {
    this.jobExecutorThreadCount = jobExecutorThreadCount;
  }

  public void setElasticSearchConfiguration(ElasticSearchConfiguration elasticSearchConfiguration) {
    this.elasticSearchConfiguration = elasticSearchConfiguration;
  }

  public void setOpenSearchConfiguration(OpenSearchConfiguration openSearchConfiguration) {
    this.openSearchConfiguration = openSearchConfiguration;
  }

  public void setConfigJsonContext(ReadContext configJsonContext) {
    this.configJsonContext = configJsonContext;
  }

  public void setSecurityConfiguration(SecurityConfiguration securityConfiguration) {
    this.securityConfiguration = securityConfiguration;
  }

  public void setUsersConfiguration(UsersConfiguration usersConfiguration) {
    this.usersConfiguration = usersConfiguration;
  }

  public void setConfiguredEngines(Map<String, EngineConfiguration> configuredEngines) {
    this.configuredEngines = configuredEngines;
  }

  public void setConfiguredZeebe(ZeebeConfiguration configuredZeebe) {
    this.configuredZeebe = configuredZeebe;
  }

  public void setEngineDateFormat(String engineDateFormat) {
    this.engineDateFormat = engineDateFormat;
  }

  public void setInitialBackoff(Long initialBackoff) {
    this.initialBackoff = initialBackoff;
  }

  public void setMaximumBackoff(Long maximumBackoff) {
    this.maximumBackoff = maximumBackoff;
  }

  public void setEngineConnectTimeout(Integer engineConnectTimeout) {
    this.engineConnectTimeout = engineConnectTimeout;
  }

  public void setEngineReadTimeout(Integer engineReadTimeout) {
    this.engineReadTimeout = engineReadTimeout;
  }

  public void setCurrentTimeBackoffMilliseconds(Integer currentTimeBackoffMilliseconds) {
    this.currentTimeBackoffMilliseconds = currentTimeBackoffMilliseconds;
  }

  public void setEngineImportProcessInstanceMaxPageSize(
      Integer engineImportProcessInstanceMaxPageSize) {
    this.engineImportProcessInstanceMaxPageSize = engineImportProcessInstanceMaxPageSize;
  }

  public void setEngineImportVariableInstanceMaxPageSize(
      Integer engineImportVariableInstanceMaxPageSize) {
    this.engineImportVariableInstanceMaxPageSize = engineImportVariableInstanceMaxPageSize;
  }

  public void setEngineImportVariableIncludeObjectVariableValue(
      Boolean engineImportVariableIncludeObjectVariableValue) {
    this.engineImportVariableIncludeObjectVariableValue =
        engineImportVariableIncludeObjectVariableValue;
  }

  public void setEngineImportProcessDefinitionXmlMaxPageSize(
      Integer engineImportProcessDefinitionXmlMaxPageSize) {
    this.engineImportProcessDefinitionXmlMaxPageSize = engineImportProcessDefinitionXmlMaxPageSize;
  }

  public void setEngineImportProcessDefinitionMaxPageSize(
      Integer engineImportProcessDefinitionMaxPageSize) {
    this.engineImportProcessDefinitionMaxPageSize = engineImportProcessDefinitionMaxPageSize;
  }

  public void setEngineImportActivityInstanceMaxPageSize(
      Integer engineImportActivityInstanceMaxPageSize) {
    this.engineImportActivityInstanceMaxPageSize = engineImportActivityInstanceMaxPageSize;
  }

  public void setEngineImportIncidentMaxPageSize(Integer engineImportIncidentMaxPageSize) {
    this.engineImportIncidentMaxPageSize = engineImportIncidentMaxPageSize;
  }

  public void setEngineImportUserTaskInstanceMaxPageSize(
      Integer engineImportUserTaskInstanceMaxPageSize) {
    this.engineImportUserTaskInstanceMaxPageSize = engineImportUserTaskInstanceMaxPageSize;
  }

  public void setEngineImportIdentityLinkLogsMaxPageSize(
      Integer engineImportIdentityLinkLogsMaxPageSize) {
    this.engineImportIdentityLinkLogsMaxPageSize = engineImportIdentityLinkLogsMaxPageSize;
  }

  public void setEngineImportUserOperationLogsMaxPageSize(
      Integer engineImportUserOperationLogsMaxPageSize) {
    this.engineImportUserOperationLogsMaxPageSize = engineImportUserOperationLogsMaxPageSize;
  }

  public void setEngineImportDecisionDefinitionXmlMaxPageSize(
      Integer engineImportDecisionDefinitionXmlMaxPageSize) {
    this.engineImportDecisionDefinitionXmlMaxPageSize =
        engineImportDecisionDefinitionXmlMaxPageSize;
  }

  public void setEngineImportDecisionDefinitionMaxPageSize(
      Integer engineImportDecisionDefinitionMaxPageSize) {
    this.engineImportDecisionDefinitionMaxPageSize = engineImportDecisionDefinitionMaxPageSize;
  }

  public void setEngineImportDecisionInstanceMaxPageSize(
      Integer engineImportDecisionInstanceMaxPageSize) {
    this.engineImportDecisionInstanceMaxPageSize = engineImportDecisionInstanceMaxPageSize;
  }

  public void setEngineImportTenantMaxPageSize(Integer engineImportTenantMaxPageSize) {
    this.engineImportTenantMaxPageSize = engineImportTenantMaxPageSize;
  }

  public void setEngineImportGroupMaxPageSize(Integer engineImportGroupMaxPageSize) {
    this.engineImportGroupMaxPageSize = engineImportGroupMaxPageSize;
  }

  public void setEngineImportAuthorizationMaxPageSize(
      Integer engineImportAuthorizationMaxPageSize) {
    this.engineImportAuthorizationMaxPageSize = engineImportAuthorizationMaxPageSize;
  }

  public void setImportIndexAutoStorageIntervalInSec(Integer importIndexAutoStorageIntervalInSec) {
    this.importIndexAutoStorageIntervalInSec = importIndexAutoStorageIntervalInSec;
  }

  public void setImportDmnDataEnabled(Boolean importDmnDataEnabled) {
    this.importDmnDataEnabled = importDmnDataEnabled;
  }

  public void setImportUserTaskWorkerDataEnabled(Boolean importUserTaskWorkerDataEnabled) {
    this.importUserTaskWorkerDataEnabled = importUserTaskWorkerDataEnabled;
  }

  public void setSkipDataAfterNestedDocLimitReached(Boolean skipDataAfterNestedDocLimitReached) {
    this.skipDataAfterNestedDocLimitReached = skipDataAfterNestedDocLimitReached;
  }

  public void setCustomerOnboarding(Boolean customerOnboarding) {
    this.customerOnboarding = customerOnboarding;
  }

  public void setContainerHost(String containerHost) {
    this.containerHost = containerHost;
  }

  public void setContextPath(String contextPath) {
    this.contextPath = contextPath;
  }

  public void setContainerKeystorePassword(String containerKeystorePassword) {
    this.containerKeystorePassword = containerKeystorePassword;
  }

  public void setContainerKeystoreLocation(String containerKeystoreLocation) {
    this.containerKeystoreLocation = containerKeystoreLocation;
  }

  public void setContainerEnableSniCheck(Boolean containerEnableSniCheck) {
    this.containerEnableSniCheck = containerEnableSniCheck;
  }

  public void setContainerHttpsPort(Integer containerHttpsPort) {
    this.containerHttpsPort = containerHttpsPort;
  }

  public void setActuatorPort(Integer actuatorPort) {
    this.actuatorPort = actuatorPort;
  }

  public void setContainerHttp2Enabled(Boolean containerHttp2Enabled) {
    this.containerHttp2Enabled = containerHttp2Enabled;
  }

  public void setContainerAccessUrl(Optional<String> containerAccessUrl) {
    this.containerAccessUrl = containerAccessUrl;
  }

  public void setMaxRequestHeaderSizeInBytes(Integer maxRequestHeaderSizeInBytes) {
    this.maxRequestHeaderSizeInBytes = maxRequestHeaderSizeInBytes;
  }

  public void setMaxResponseHeaderSizeInBytes(Integer maxResponseHeaderSizeInBytes) {
    this.maxResponseHeaderSizeInBytes = maxResponseHeaderSizeInBytes;
  }

  public void setContainerHttpPort(Optional<Integer> containerHttpPort) {
    this.containerHttpPort = containerHttpPort;
  }

  public void setMaxStatusConnections(Integer maxStatusConnections) {
    this.maxStatusConnections = maxStatusConnections;
  }

  public void setEmailEnabled(Boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
  }

  public void setNotificationEmailAddress(String notificationEmailAddress) {
    this.notificationEmailAddress = notificationEmailAddress;
  }

  public void setNotificationEmailHostname(String notificationEmailHostname) {
    this.notificationEmailHostname = notificationEmailHostname;
  }

  public void setNotificationEmailPort(Integer notificationEmailPort) {
    this.notificationEmailPort = notificationEmailPort;
  }

  public void setNotificationEmailCheckServerIdentity(
      Boolean notificationEmailCheckServerIdentity) {
    this.notificationEmailCheckServerIdentity = notificationEmailCheckServerIdentity;
  }

  public void setNotificationEmailCompanyBranding(String notificationEmailCompanyBranding) {
    this.notificationEmailCompanyBranding = notificationEmailCompanyBranding;
  }

  public void setEmailAuthenticationConfiguration(
      EmailAuthenticationConfiguration emailAuthenticationConfiguration) {
    this.emailAuthenticationConfiguration = emailAuthenticationConfiguration;
  }

  public void setConfiguredWebhooks(Map<String, WebhookConfiguration> configuredWebhooks) {
    this.configuredWebhooks = configuredWebhooks;
  }

  public void setDigestCronTrigger(String digestCronTrigger) {
    this.digestCronTrigger = digestCronTrigger;
  }

  public void setEntityConfiguration(EntityConfiguration entityConfiguration) {
    this.entityConfiguration = entityConfiguration;
  }

  public void setCsvConfiguration(CsvConfiguration csvConfiguration) {
    this.csvConfiguration = csvConfiguration;
  }

  public void setQuartzProperties(Properties quartzProperties) {
    this.quartzProperties = quartzProperties;
  }

  public void setCleanupServiceConfiguration(CleanupConfiguration cleanupServiceConfiguration) {
    this.cleanupServiceConfiguration = cleanupServiceConfiguration;
  }

  public void setSharingEnabled(Boolean sharingEnabled) {
    this.sharingEnabled = sharingEnabled;
  }

  public void setAvailableLocales(List<String> availableLocales) {
    this.availableLocales = availableLocales;
  }

  public void setFallbackLocale(String fallbackLocale) {
    this.fallbackLocale = fallbackLocale;
  }

  public void setUiConfiguration(UIConfiguration uiConfiguration) {
    this.uiConfiguration = uiConfiguration;
  }

  public void setUserTaskIdentityCacheConfiguration(
      UserTaskIdentityCacheConfiguration userTaskIdentityCacheConfiguration) {
    this.userTaskIdentityCacheConfiguration = userTaskIdentityCacheConfiguration;
  }

  public void setUserIdentityCacheConfiguration(
      UserIdentityCacheConfiguration userIdentityCacheConfiguration) {
    this.userIdentityCacheConfiguration = userIdentityCacheConfiguration;
  }

  public void setTelemetryConfiguration(TelemetryConfiguration telemetryConfiguration) {
    this.telemetryConfiguration = telemetryConfiguration;
  }

  public void setExternalVariableConfiguration(
      ExternalVariableConfiguration externalVariableConfiguration) {
    this.externalVariableConfiguration = externalVariableConfiguration;
  }

  public void setCaches(GlobalCacheConfiguration caches) {
    this.caches = caches;
  }

  public void setAnalytics(AnalyticsConfiguration analytics) {
    this.analytics = analytics;
  }

  public void setOptimizeApiConfiguration(OptimizeApiConfiguration optimizeApiConfiguration) {
    this.optimizeApiConfiguration = optimizeApiConfiguration;
  }

  public void setOnboarding(OnboardingConfiguration onboarding) {
    this.onboarding = onboarding;
  }

  public void setPanelNotificationConfiguration(
      PanelNotificationConfiguration panelNotificationConfiguration) {
    this.panelNotificationConfiguration = panelNotificationConfiguration;
  }

  public void setM2mAuth0ClientConfiguration(
      M2mAuth0ClientConfiguration m2mAuth0ClientConfiguration) {
    this.m2mAuth0ClientConfiguration = m2mAuth0ClientConfiguration;
  }

  public void setMultiTenancyEnabled(Boolean multiTenancyEnabled) {
    this.multiTenancyEnabled = multiTenancyEnabled;
  }
}
