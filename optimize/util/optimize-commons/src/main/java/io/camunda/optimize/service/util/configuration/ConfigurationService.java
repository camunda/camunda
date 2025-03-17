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
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPTIMIZE_MODE_PROFILES;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.PANEL_NOTIFICATION_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.TELEMETRY_CONFIGURATION;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.UI_CONFIGURATION;
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
import io.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.configuration.analytics.AnalyticsConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.engine.UserIdentityCacheConfiguration;
import io.camunda.optimize.service.util.configuration.engine.UserTaskIdentityCacheConfiguration;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.SecurityConfiguration;
import io.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import io.camunda.optimize.service.util.configuration.users.UsersConfiguration;
import io.camunda.optimize.util.SuppressionConstants;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.core.env.Environment;

public class ConfigurationService {

  private static final String ERROR_NO_ENGINE_WITH_ALIAS = "No Engine configured with alias ";

  // @formatter:off
  private static final TypeRef<List<String>> LIST_OF_STRINGS_TYPE_REF = new TypeRef<>() {};
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ConfigurationService.class);
  // @formatter:on
  // job executor settings
  protected Integer jobExecutorQueueSize;
  protected Integer jobExecutorThreadCount;
  private ElasticSearchConfiguration elasticSearchConfiguration;
  private OpenSearchConfiguration openSearchConfiguration;
  private ReadContext configJsonContext;
  private SecurityConfiguration securityConfiguration;
  private UsersConfiguration usersConfiguration;
  private ZeebeConfiguration configuredZeebe;
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
            .filter(OPTIMIZE_MODE_PROFILES::contains)
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
      LOG.error(reason);
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

  public void setElasticSearchConfiguration(
      final ElasticSearchConfiguration elasticSearchConfiguration) {
    this.elasticSearchConfiguration = elasticSearchConfiguration;
  }

  public OpenSearchConfiguration getOpenSearchConfiguration() {
    if (openSearchConfiguration == null) {
      openSearchConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.OPENSEARCH, OpenSearchConfiguration.class);
    }
    return openSearchConfiguration;
  }

  public void setOpenSearchConfiguration(final OpenSearchConfiguration openSearchConfiguration) {
    this.openSearchConfiguration = openSearchConfiguration;
  }

  ReadContext getConfigJsonContext() {
    return configJsonContext;
  }

  public void setConfigJsonContext(final ReadContext configJsonContext) {
    this.configJsonContext = configJsonContext;
  }

  public ZeebeConfiguration getConfiguredZeebe() {
    if (configuredZeebe == null) {
      configuredZeebe =
          configJsonContext.read(
              ConfigurationServiceConstants.CONFIGURED_ZEEBE, ZeebeConfiguration.class);
    }
    return configuredZeebe;
  }

  public void setConfiguredZeebe(final ZeebeConfiguration configuredZeebe) {
    this.configuredZeebe = configuredZeebe;
  }

  public SecurityConfiguration getSecurityConfiguration() {
    if (securityConfiguration == null) {
      securityConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.SECURITY, SecurityConfiguration.class);
    }
    return securityConfiguration;
  }

  public void setSecurityConfiguration(final SecurityConfiguration securityConfiguration) {
    this.securityConfiguration = securityConfiguration;
  }

  public UsersConfiguration getUsersConfiguration() {
    if (usersConfiguration == null) {
      usersConfiguration =
          configJsonContext.read(ConfigurationServiceConstants.USERS, UsersConfiguration.class);
    }
    return usersConfiguration;
  }

  public void setUsersConfiguration(final UsersConfiguration usersConfiguration) {
    this.usersConfiguration = usersConfiguration;
  }

  @JsonIgnore
  public AuthConfiguration getAuthConfiguration() {
    return getSecurityConfiguration().getAuth();
  }

  public int getImportIndexAutoStorageIntervalInSec() {
    if (importIndexAutoStorageIntervalInSec == null) {
      importIndexAutoStorageIntervalInSec =
          configJsonContext.read(
              ConfigurationServiceConstants.IMPORT_INDEX_AUTO_STORAGE_INTERVAL, Integer.class);
    }
    return importIndexAutoStorageIntervalInSec;
  }

  public void setImportIndexAutoStorageIntervalInSec(
      final Integer importIndexAutoStorageIntervalInSec) {
    this.importIndexAutoStorageIntervalInSec = importIndexAutoStorageIntervalInSec;
  }

  public long getInitialBackoff() {
    if (initialBackoff == null) {
      initialBackoff =
          configJsonContext.read(
              ConfigurationServiceConstants.INITIAL_BACKOFF_INTERVAL, Long.class);
    }
    return initialBackoff;
  }

  public void setInitialBackoff(final Long initialBackoff) {
    this.initialBackoff = initialBackoff;
  }

  public long getMaximumBackoff() {
    if (maximumBackoff == null) {
      maximumBackoff =
          configJsonContext.read(ConfigurationServiceConstants.MAXIMUM_BACK_OFF, Long.class);
    }
    return maximumBackoff;
  }

  public void setMaximumBackoff(final Long maximumBackoff) {
    this.maximumBackoff = maximumBackoff;
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

  public void setCurrentTimeBackoffMilliseconds(final Integer currentTimeBackoffMilliseconds) {
    this.currentTimeBackoffMilliseconds = currentTimeBackoffMilliseconds;
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

  public void setEngineImportProcessInstanceMaxPageSize(
      final Integer engineImportProcessInstanceMaxPageSize) {
    this.engineImportProcessInstanceMaxPageSize = engineImportProcessInstanceMaxPageSize;
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

  public void setEngineImportVariableInstanceMaxPageSize(
      final Integer engineImportVariableInstanceMaxPageSize) {
    this.engineImportVariableInstanceMaxPageSize = engineImportVariableInstanceMaxPageSize;
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

  public void setEngineImportVariableIncludeObjectVariableValue(
      final Boolean engineImportVariableIncludeObjectVariableValue) {
    this.engineImportVariableIncludeObjectVariableValue =
        engineImportVariableIncludeObjectVariableValue;
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

  public void setEngineImportProcessDefinitionXmlMaxPageSize(
      final Integer engineImportProcessDefinitionXmlMaxPageSize) {
    this.engineImportProcessDefinitionXmlMaxPageSize = engineImportProcessDefinitionXmlMaxPageSize;
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

  public void setEngineImportProcessDefinitionMaxPageSize(
      final Integer engineImportProcessDefinitionMaxPageSize) {
    this.engineImportProcessDefinitionMaxPageSize = engineImportProcessDefinitionMaxPageSize;
  }

  public boolean getSharingEnabled() {
    if (sharingEnabled == null) {
      sharingEnabled =
          configJsonContext.read(ConfigurationServiceConstants.SHARING_ENABLED, Boolean.class);
    }
    return Optional.ofNullable(sharingEnabled).orElse(false);
  }

  public void setSharingEnabled(final Boolean sharingEnabled) {
    this.sharingEnabled = sharingEnabled;
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

  public void setEngineImportDecisionDefinitionXmlMaxPageSize(
      final Integer engineImportDecisionDefinitionXmlMaxPageSize) {
    this.engineImportDecisionDefinitionXmlMaxPageSize =
        engineImportDecisionDefinitionXmlMaxPageSize;
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

  public void setEngineImportDecisionDefinitionMaxPageSize(
      final Integer engineImportDecisionDefinitionMaxPageSize) {
    this.engineImportDecisionDefinitionMaxPageSize = engineImportDecisionDefinitionMaxPageSize;
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

  public void setEngineImportDecisionInstanceMaxPageSize(
      final Integer engineImportDecisionInstanceMaxPageSize) {
    this.engineImportDecisionInstanceMaxPageSize = engineImportDecisionInstanceMaxPageSize;
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

  public void setEngineImportTenantMaxPageSize(final Integer engineImportTenantMaxPageSize) {
    this.engineImportTenantMaxPageSize = engineImportTenantMaxPageSize;
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

  public void setEngineImportGroupMaxPageSize(final Integer engineImportGroupMaxPageSize) {
    this.engineImportGroupMaxPageSize = engineImportGroupMaxPageSize;
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

  public void setEngineImportAuthorizationMaxPageSize(
      final Integer engineImportAuthorizationMaxPageSize) {
    this.engineImportAuthorizationMaxPageSize = engineImportAuthorizationMaxPageSize;
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

  public void setEngineImportActivityInstanceMaxPageSize(
      final Integer engineImportActivityInstanceMaxPageSize) {
    this.engineImportActivityInstanceMaxPageSize = engineImportActivityInstanceMaxPageSize;
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

  public void setEngineImportIncidentMaxPageSize(final Integer engineImportIncidentMaxPageSize) {
    this.engineImportIncidentMaxPageSize = engineImportIncidentMaxPageSize;
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

  public void setEngineImportUserTaskInstanceMaxPageSize(
      final Integer engineImportUserTaskInstanceMaxPageSize) {
    this.engineImportUserTaskInstanceMaxPageSize = engineImportUserTaskInstanceMaxPageSize;
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

  public void setEngineImportIdentityLinkLogsMaxPageSize(
      final Integer engineImportIdentityLinkLogsMaxPageSize) {
    this.engineImportIdentityLinkLogsMaxPageSize = engineImportIdentityLinkLogsMaxPageSize;
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

  public void setEngineImportUserOperationLogsMaxPageSize(
      final Integer engineImportUserOperationLogsMaxPageSize) {
    this.engineImportUserOperationLogsMaxPageSize = engineImportUserOperationLogsMaxPageSize;
  }

  public String getContainerHost() {
    if (containerHost == null) {
      containerHost = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_HOST);
    }
    return containerHost;
  }

  public void setContainerHost(final String containerHost) {
    this.containerHost = containerHost;
  }

  public Optional<String> getContextPath() {
    if (contextPath == null) {
      contextPath = configJsonContext.read(ConfigurationServiceConstants.CONTAINER_CONTEXT_PATH);
    }
    return Optional.ofNullable(contextPath);
  }

  public void setContextPath(final String contextPath) {
    this.contextPath = contextPath;
  }

  public String getContainerKeystorePassword() {
    if (containerKeystorePassword == null) {
      containerKeystorePassword =
          configJsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_PASSWORD);
    }
    return containerKeystorePassword;
  }

  public void setContainerKeystorePassword(final String containerKeystorePassword) {
    this.containerKeystorePassword = containerKeystorePassword;
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

  public void setContainerAccessUrl(final Optional<String> containerAccessUrl) {
    this.containerAccessUrl = containerAccessUrl;
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

  public void setMaxRequestHeaderSizeInBytes(final Integer maxRequestHeaderSizeInBytes) {
    this.maxRequestHeaderSizeInBytes = maxRequestHeaderSizeInBytes;
  }

  public Integer getMaxResponseHeaderSizeInBytes() {
    if (maxResponseHeaderSizeInBytes == null) {
      maxResponseHeaderSizeInBytes =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_MAX_RESPONSE_HEADER_IN_BYTES, Integer.class);
    }
    return maxResponseHeaderSizeInBytes;
  }

  public void setMaxResponseHeaderSizeInBytes(final Integer maxResponseHeaderSizeInBytes) {
    this.maxResponseHeaderSizeInBytes = maxResponseHeaderSizeInBytes;
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

  public void setContainerKeystoreLocation(final String containerKeystoreLocation) {
    this.containerKeystoreLocation = containerKeystoreLocation;
  }

  public Boolean getContainerEnableSniCheck() {
    if (containerEnableSniCheck == null) {
      containerEnableSniCheck =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_ENABLE_SNI_CHECK, Boolean.class);
    }
    return containerEnableSniCheck;
  }

  public void setContainerEnableSniCheck(final Boolean containerEnableSniCheck) {
    this.containerEnableSniCheck = containerEnableSniCheck;
  }

  public Boolean getContainerHttp2Enabled() {
    if (containerHttp2Enabled == null) {
      containerHttp2Enabled =
          configJsonContext.read(
              ConfigurationServiceConstants.CONTAINER_HTTP2_ENABLED, Boolean.class);
    }
    return containerHttp2Enabled;
  }

  public void setContainerHttp2Enabled(final Boolean containerHttp2Enabled) {
    this.containerHttp2Enabled = containerHttp2Enabled;
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

  public void setContainerHttpsPort(final Integer containerHttpsPort) {
    this.containerHttpsPort = containerHttpsPort;
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

  public void setActuatorPort(final Integer actuatorPort) {
    this.actuatorPort = actuatorPort;
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

  public void setContainerHttpPort(final Optional<Integer> containerHttpPort) {
    this.containerHttpPort = containerHttpPort;
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

  public void setMaxStatusConnections(final Integer maxStatusConnections) {
    this.maxStatusConnections = maxStatusConnections;
  }

  public boolean isImportEnabled(final SchedulerConfig dataSourceDto) {
    if (dataSourceDto instanceof ZeebeConfigDto) {
      return getConfiguredZeebe().isEnabled();
    } else if (dataSourceDto instanceof IngestedDataSourceDto) {
      return getExternalVariableConfiguration().getImportConfiguration().isEnabled();
    }
    throw new OptimizeConfigurationException("Invalid data import source");
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

  public void setQuartzProperties(final Properties quartzProperties) {
    this.quartzProperties = quartzProperties;
  }

  public boolean getEmailEnabled() {
    if (emailEnabled == null) {
      emailEnabled =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_ENABLED, Boolean.class);
    }
    return emailEnabled;
  }

  public void setEmailEnabled(final Boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
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

  public void setEmailAuthenticationConfiguration(
      final EmailAuthenticationConfiguration emailAuthenticationConfiguration) {
    this.emailAuthenticationConfiguration = emailAuthenticationConfiguration;
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

  public void setImportDmnDataEnabled(final Boolean importDmnDataEnabled) {
    this.importDmnDataEnabled = importDmnDataEnabled;
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

  public void setSkipDataAfterNestedDocLimitReached(
      final Boolean skipDataAfterNestedDocLimitReached) {
    this.skipDataAfterNestedDocLimitReached = skipDataAfterNestedDocLimitReached;
  }

  @JsonIgnore
  public boolean isImportUserTaskWorkerDataEnabled() {
    return getImportUserTaskWorkerDataEnabled();
  }

  public void setImportUserTaskWorkerDataEnabled(final Boolean importUserTaskWorkerDataEnabled) {
    this.importUserTaskWorkerDataEnabled = importUserTaskWorkerDataEnabled;
  }

  public String getNotificationEmailAddress() {
    if (notificationEmailAddress == null) {
      notificationEmailAddress =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_ADDRESS);
    }
    return notificationEmailAddress;
  }

  public void setNotificationEmailAddress(final String notificationEmailAddress) {
    this.notificationEmailAddress = notificationEmailAddress;
  }

  public String getNotificationEmailHostname() {
    if (notificationEmailHostname == null) {
      notificationEmailHostname =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_HOSTNAME);
    }
    return notificationEmailHostname;
  }

  public void setNotificationEmailHostname(final String notificationEmailHostname) {
    this.notificationEmailHostname = notificationEmailHostname;
  }

  public Integer getNotificationEmailPort() {
    if (notificationEmailPort == null) {
      notificationEmailPort =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_PORT, Integer.class);
    }
    return notificationEmailPort;
  }

  public void setNotificationEmailPort(final Integer notificationEmailPort) {
    this.notificationEmailPort = notificationEmailPort;
  }

  public Boolean getNotificationEmailCheckServerIdentity() {
    return Optional.ofNullable(notificationEmailCheckServerIdentity)
        .orElse(
            configJsonContext.read(
                ConfigurationServiceConstants.CHECK_SERVER_IDENTITY, Boolean.class));
  }

  public void setNotificationEmailCheckServerIdentity(
      final Boolean notificationEmailCheckServerIdentity) {
    this.notificationEmailCheckServerIdentity = notificationEmailCheckServerIdentity;
  }

  public String getNotificationEmailCompanyBranding() {
    if (notificationEmailCompanyBranding == null) {
      notificationEmailCompanyBranding =
          configJsonContext.read(ConfigurationServiceConstants.EMAIL_BRANDING, String.class);
    }
    return notificationEmailCompanyBranding;
  }

  public void setNotificationEmailCompanyBranding(final String notificationEmailCompanyBranding) {
    this.notificationEmailCompanyBranding = notificationEmailCompanyBranding;
  }

  public String getDigestCronTrigger() {
    if (digestCronTrigger == null) {
      digestCronTrigger =
          configJsonContext.read(ConfigurationServiceConstants.DIGEST_CRON_TRIGGER, String.class);
    }
    return digestCronTrigger;
  }

  public void setDigestCronTrigger(final String digestCronTrigger) {
    this.digestCronTrigger = digestCronTrigger;
  }

  public EntityConfiguration getEntityConfiguration() {
    if (entityConfiguration == null) {
      entityConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.ENTITY_CONFIGURATION, EntityConfiguration.class);
    }
    return entityConfiguration;
  }

  public void setEntityConfiguration(final EntityConfiguration entityConfiguration) {
    this.entityConfiguration = entityConfiguration;
  }

  public CsvConfiguration getCsvConfiguration() {
    if (csvConfiguration == null) {
      csvConfiguration =
          configJsonContext.read(
              ConfigurationServiceConstants.CSV_CONFIGURATION, CsvConfiguration.class);
    }
    return csvConfiguration;
  }

  public void setCsvConfiguration(final CsvConfiguration csvConfiguration) {
    this.csvConfiguration = csvConfiguration;
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

  public void setCleanupServiceConfiguration(
      final CleanupConfiguration cleanupServiceConfiguration) {
    this.cleanupServiceConfiguration = cleanupServiceConfiguration;
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

  public void setAvailableLocales(final List<String> availableLocales) {
    this.availableLocales = availableLocales;
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

  public void setFallbackLocale(final String fallbackLocale) {
    this.fallbackLocale = fallbackLocale;
  }

  public UIConfiguration getUiConfiguration() {
    if (uiConfiguration == null) {
      uiConfiguration = configJsonContext.read(UI_CONFIGURATION, UIConfiguration.class);
      uiConfiguration.validate();
    }
    return uiConfiguration;
  }

  public void setUiConfiguration(final UIConfiguration uiConfiguration) {
    this.uiConfiguration = uiConfiguration;
  }

  public UserIdentityCacheConfiguration getUserIdentityCacheConfiguration() {
    if (userIdentityCacheConfiguration == null) {
      userIdentityCacheConfiguration =
          configJsonContext.read(IDENTITY_SYNC_CONFIGURATION, UserIdentityCacheConfiguration.class);
    }
    return userIdentityCacheConfiguration;
  }

  public void setUserIdentityCacheConfiguration(
      final UserIdentityCacheConfiguration userIdentityCacheConfiguration) {
    this.userIdentityCacheConfiguration = userIdentityCacheConfiguration;
  }

  public UserTaskIdentityCacheConfiguration getUserTaskIdentityCacheConfiguration() {
    if (userTaskIdentityCacheConfiguration == null) {
      userTaskIdentityCacheConfiguration =
          configJsonContext.read(
              IMPORT_USER_TASK_IDENTITY_META_DATA, UserTaskIdentityCacheConfiguration.class);
    }
    return userTaskIdentityCacheConfiguration;
  }

  public void setUserTaskIdentityCacheConfiguration(
      final UserTaskIdentityCacheConfiguration userTaskIdentityCacheConfiguration) {
    this.userTaskIdentityCacheConfiguration = userTaskIdentityCacheConfiguration;
  }

  public OptimizeApiConfiguration getOptimizeApiConfiguration() {
    if (optimizeApiConfiguration == null) {
      optimizeApiConfiguration =
          configJsonContext.read(OPTIMIZE_API_CONFIGURATION, OptimizeApiConfiguration.class);
    }
    return optimizeApiConfiguration;
  }

  public void setOptimizeApiConfiguration(final OptimizeApiConfiguration optimizeApiConfiguration) {
    this.optimizeApiConfiguration = optimizeApiConfiguration;
  }

  public TelemetryConfiguration getTelemetryConfiguration() {
    if (telemetryConfiguration == null) {
      telemetryConfiguration =
          configJsonContext.read(TELEMETRY_CONFIGURATION, TelemetryConfiguration.class);
    }
    return telemetryConfiguration;
  }

  public void setTelemetryConfiguration(final TelemetryConfiguration telemetryConfiguration) {
    this.telemetryConfiguration = telemetryConfiguration;
  }

  public ExternalVariableConfiguration getExternalVariableConfiguration() {
    if (externalVariableConfiguration == null) {
      externalVariableConfiguration =
          configJsonContext.read(
              EXTERNAL_VARIABLE_CONFIGURATION, ExternalVariableConfiguration.class);
    }
    return externalVariableConfiguration;
  }

  public void setExternalVariableConfiguration(
      final ExternalVariableConfiguration externalVariableConfiguration) {
    this.externalVariableConfiguration = externalVariableConfiguration;
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

  public void setCaches(final GlobalCacheConfiguration caches) {
    this.caches = caches;
  }

  public AnalyticsConfiguration getAnalytics() {
    if (analytics == null) {
      analytics = configJsonContext.read(ANALYTICS_CONFIGURATION, AnalyticsConfiguration.class);
    }
    return analytics;
  }

  public void setAnalytics(final AnalyticsConfiguration analytics) {
    this.analytics = analytics;
  }

  public OnboardingConfiguration getOnboarding() {
    if (onboarding == null) {
      onboarding = configJsonContext.read(ONBOARDING_CONFIGURATION, OnboardingConfiguration.class);
    }
    return onboarding;
  }

  public void setOnboarding(final OnboardingConfiguration onboarding) {
    this.onboarding = onboarding;
  }

  public PanelNotificationConfiguration getPanelNotificationConfiguration() {
    if (panelNotificationConfiguration == null) {
      panelNotificationConfiguration =
          configJsonContext.read(
              PANEL_NOTIFICATION_CONFIGURATION, PanelNotificationConfiguration.class);
    }
    return panelNotificationConfiguration;
  }

  public void setPanelNotificationConfiguration(
      final PanelNotificationConfiguration panelNotificationConfiguration) {
    this.panelNotificationConfiguration = panelNotificationConfiguration;
  }

  public M2mAuth0ClientConfiguration getM2mAuth0ClientConfiguration() {
    if (m2mAuth0ClientConfiguration == null) {
      m2mAuth0ClientConfiguration =
          configJsonContext.read(M2M_CLIENT_CONFIGURATION, M2mAuth0ClientConfiguration.class);
    }
    return m2mAuth0ClientConfiguration;
  }

  public void setM2mAuth0ClientConfiguration(
      final M2mAuth0ClientConfiguration m2mAuth0ClientConfiguration) {
    this.m2mAuth0ClientConfiguration = m2mAuth0ClientConfiguration;
  }

  public boolean isMultiTenancyEnabled() {
    if (multiTenancyEnabled == null) {
      multiTenancyEnabled =
          configJsonContext.read(ConfigurationServiceConstants.MULTITENANCY_ENABLED, Boolean.class);
    }
    return multiTenancyEnabled;
  }

  public void setMultiTenancyEnabled(final Boolean multiTenancyEnabled) {
    this.multiTenancyEnabled = multiTenancyEnabled;
  }

  public Integer getJobExecutorQueueSize() {
    if (jobExecutorQueueSize == null) {
      jobExecutorQueueSize =
          configJsonContext.read(
              ConfigurationServiceConstants.DATABASE_MAX_JOB_QUEUE_SIZE, Integer.class);
    }
    return jobExecutorQueueSize;
  }

  public void setJobExecutorQueueSize(final Integer jobExecutorQueueSize) {
    this.jobExecutorQueueSize = jobExecutorQueueSize;
  }

  public Integer getJobExecutorThreadCount() {
    if (jobExecutorThreadCount == null) {
      jobExecutorThreadCount =
          configJsonContext.read(
              ConfigurationServiceConstants.DATABASE_IMPORT_EXECUTOR_THREAD_COUNT, Integer.class);
    }
    return jobExecutorThreadCount;
  }

  public void setJobExecutorThreadCount(final Integer jobExecutorThreadCount) {
    this.jobExecutorThreadCount = jobExecutorThreadCount;
  }

  public void setCustomerOnboarding(final Boolean customerOnboarding) {
    this.customerOnboarding = customerOnboarding;
  }
}
