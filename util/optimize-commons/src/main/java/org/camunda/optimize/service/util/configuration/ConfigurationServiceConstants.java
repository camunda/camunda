/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConfigurationServiceConstants {
  public static final String SECURITY = "$.security";
  public static final String USERS = "$.users";
  public static final String CONFIGURED_ENGINES = "$.engines";
  public static final String CONFIGURED_ZEEBE = "$.zeebe";

  public static final String QUARTZ_JOB_STORE_CLASS = "$.alerting.quartz.jobStore";

  public static final String EMAIL_ADDRESS = "$.email.address";
  public static final String EMAIL_ENABLED = "$.email.enabled";
  public static final String EMAIL_HOSTNAME = "$.email.hostname";
  public static final String EMAIL_PORT = "$.email.port";
  public static final String EMAIL_BRANDING = "$.email.companyBranding";

  public static final String EMAIL_AUTHENTICATION = "$.email.authentication";

  public static final String CONFIGURED_WEBHOOKS = "$.webhookAlerting.webhooks";
  public static final String DIGEST_CRON_TRIGGER = "$.digest.cronTrigger";
  //@formatter:off
  public static final String ELASTICSEARCH_MAX_JOB_QUEUE_SIZE = "$.import.elasticsearchJobExecutorQueueSize";
  public static final String ELASTICSEARCH_IMPORT_EXECUTOR_THREAD_COUNT = "$.import.elasticsearchJobExecutorThreadCount";

  public static final String IMPORT_CURRENT_TIME_BACKOFF_MILLISECONDS = "$.import.currentTimeBackoffMilliseconds";
  public static final String IMPORT_SKIP_DATA_AFTER_NESTED_DOC_LIMIT_REACHED = "$.import.skipDataAfterNestedDocLimitReached";
  public static final String ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE = "$.import.data.process-definition-xml.maxPageSize";
  public static final String ENGINE_IMPORT_PROCESS_DEFINITION_MAX_PAGE_SIZE = "$.import.data.process-definition.maxPageSize";
  public static final String ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE = "$.import.data.activity-instance.maxPageSize";
  public static final String ENGINE_IMPORT_INCIDENT_MAX_PAGE_SIZE = "$.import.data.incident.maxPageSize";
  public static final String ENGINE_IMPORT_USER_TASK_INSTANCE_MAX_PAGE_SIZE = "$.import.data.user-task-instance.maxPageSize";
  public static final String ENGINE_IMPORT_IDENTITY_LING_LOG_MAX_PAGE_SIZE = "$.import.data.identity-link-log.maxPageSize";
  public static final String ENGINE_IMPORT_USER_OPERATION_LOG_MAX_PAGE_SIZE = "$.import.data.user-operation-log.maxPageSize";
  public static final String ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE = "$.import.data.process-instance.maxPageSize";
  public static final String ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE = "$.import.data.variable.maxPageSize";
  public static final String ENGINE_IMPORT_VARIABLE_INCLUDE_OBJECT_VARIABLE_VALUE = "$.import.data.variable.includeObjectVariableValue";
  public static final String ENGINE_IMPORT_DECISION_DEFINITION_MAX_PAGE_SIZE = "$.import.data.decision-definition.maxPageSize";
  public static final String ENGINE_IMPORT_DECISION_DEFINITION_XML_MAX_PAGE_SIZE = "$.import.data.decision-definition-xml.maxPageSize";
  public static final String ENGINE_IMPORT_DECISION_INSTANCE_MAX_PAGE_SIZE = "$.import.data.decision-instance.maxPageSize";
  public static final String ENGINE_IMPORT_TENANT_MAX_PAGE_SIZE = "$.import.data.tenant.maxPageSize";
  public static final String ENGINE_IMPORT_GROUP_MAX_PAGE_SIZE = "$.import.data.group.maxPageSize";
  public static final String ENGINE_IMPORT_AUTHORIZATION_MAX_PAGE_SIZE = "$.import.data.authorization.maxPageSize";
  public static final String IMPORT_DMN_DATA = "$.import.data.dmn.enabled";
  public static final String IMPORT_USER_TASK_WORKER_DATA = "$.import.data.user-task-worker.enabled";
  public static final String IMPORT_USER_TASK_IDENTITY_META_DATA = "$.import.data.user-task-worker.metadata";
  public static final String CUSTOMER_ONBOARDING_DATA = "$.import.customer-onboarding";

  public static final String PLUGIN_BASE_DIRECTORY = "$.plugin.directory";
  public static final String VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES = "$.plugin.variableImport.basePackages";
  public static final String ENGINE_REST_FILTER_PLUGIN_BASE_PACKAGES = "$.plugin.engineRestFilter.basePackages";
  public static final String AUTHENTICATION_EXTRACTOR_BASE_PACKAGES = "$.plugin.authenticationExtractor.basePackages";
  public static final String DECISION_INPUT_IMPORT_PLUGIN_BASE_PACKAGES = "$.plugin.decisionInputImport.basePackages";
  public static final String DECISION_OUTPUT_IMPORT_PLUGIN_BASE_PACKAGES = "$.plugin.decisionOutputImport.basePackages";
  public static final String BUSINESS_KEY_IMPORT_PLUGIN_BASE_PACKAGES = "$.plugin.businessKeyImport.basePackages";
  public static final String ELASTICSEARCH_CUSTOM_HEADER_BASE_PACKAGES = "$.plugin.elasticsearchCustomHeader.basePackages";
  public static final String ELASTICSEARCH_CONNECTION_TIMEOUT = "$.es.connection.timeout";
  public static final String ELASTICSEARCH_RESPONSE_CONSUMER_BUFFER_LIMIT_MB = "$.es.connection.responseConsumerBufferLimitInMb";
  public static final String ELASTICSEARCH_SCROLL_TIMEOUT_IN_SECONDS = "$.es.scrollTimeoutInSeconds";
  public static final String ELASTICSEARCH_CONNECTION_NODES = "$.es.connection.nodes";
  public static final String ELASTICSEARCH_PROXY = "$.es.connection.proxy";
  public static final String ELASTICSEARCH_SKIP_HOSTNAME_VERIFICATION = "$.es.connection.skipHostnameVerification";
  public static final String ELASTICSEARCH_PATH_PREFIX = "$.es.connection.pathPrefix";
  public static final String ELASTICSEARCH_SNAPSHOT_REPO = "$.es.backup.repositoryName";

  public static final String ELASTICSEARCH_SECURITY_USERNAME = "$.es.security.username";
  public static final String ELASTICSEARCH_SECURITY_PASSWORD = "$.es.security.password";
  public static final String ELASTICSEARCH_SECURITY_SSL_ENABLED = "$.es.security.ssl.enabled";
  public static final String ELASTICSEARCH_SECURITY_SSL_SELF_SIGNED = "$.es.security.ssl.selfSigned";
  public static final String ELASTICSEARCH_SECURITY_SSL_CERTIFICATE = "$.es.security.ssl.certificate";
  public static final String ELASTICSEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES = "$.es.security.ssl.certificate_authorities";

  public static final String IMPORT_INDEX_AUTO_STORAGE_INTERVAL = "$.import.importIndexStorageIntervalInSec";

  public static final String ENGINE_CONNECT_TIMEOUT = "$.engine-commons.connection.timeout";
  public static final String ENGINE_READ_TIMEOUT = "$.engine-commons.read.timeout";

  public static final String INITIAL_BACKOFF_INTERVAL = "$.import.handler.backoff.initial";
  public static final String MAXIMUM_BACK_OFF = "$.import.handler.backoff.max";
  public static final String ES_AGGREGATION_BUCKET_LIMIT = "$.es.settings.aggregationBucketLimit";
  public static final String ES_REFRESH_INTERVAL = "$.es.settings.index.refresh_interval";
  public static final String ES_NUMBER_OF_REPLICAS = "$.es.settings.index.number_of_replicas";
  public static final String ES_NUMBER_OF_SHARDS = "$.es.settings.index.number_of_shards";
  public static final String ES_INDEX_PREFIX = "$.es.settings.index.prefix";
  public static final String ES_INDEX_NESTED_DOCUMENTS_LIMIT = "$.es.settings.index.nested_documents_limit";

  public static final String ENGINE_DATE_FORMAT = "$.serialization.engineDateFormat";
  public static final String CONTAINER_HOST = "$.container.host";
  public static final String CONTAINER_CONTEXT_PATH = "$.container.contextPath";
  public static final String CONTAINER_KEYSTORE_PASSWORD = "$.container.keystore.password";
  public static final String CONTAINER_KEYSTORE_LOCATION = "$.container.keystore.location";
  public static final String CONTAINER_HTTPS_PORT = "$.container.ports.https";
  public static final String CONTAINER_HTTP_PORT = "$.container.ports.http";
  public static final String CONTAINER_STATUS_MAX_CONNECTIONS = "$.container.status.connections.max";

  public static final String CONTAINER_ACCESS_URL = "$.container.accessUrl";

  public static final String ENTITY_CONFIGURATION = "$.entity";

  public static final String CSV_CONFIGURATION = "$.export.csv";
  public static final String EXPORT_CSV_DELIMITER = "$.export.csv.delimiter";

  public static final String HISTORY_CLEANUP = "$.historyCleanup";
  public static final String HISTORY_CLEANUP_PROCESS_DATA = HISTORY_CLEANUP + ".processDataCleanup";

  public static final String SHARING_ENABLED = "$.sharing.enabled";

  public static final String AVAILABLE_LOCALES = "$.locales.availableLocales";
  public static final String FALLBACK_LOCALE = "$.locales.fallbackLocale";

  public static final String DATA_ARCHIVE = "$.dataArchive";

  public static final String UI_CONFIGURATION = "$.ui";

  public static final String IDENTITY_SYNC_CONFIGURATION = "$.import.identitySync";

  public static final String EVENT_BASED_PROCESS_CONFIGURATION = "$.eventBasedProcess";

  public static final String OPTIMIZE_API_CONFIGURATION = "$.api";

  public static final String TELEMETRY_CONFIGURATION = "$.telemetry";

  public static final String EXTERNAL_VARIABLE_CONFIGURATION = "$.externalVariable";

  public static final String CACHES_CONFIGURATION = "$.caches";

  public static final String ANALYTICS_CONFIGURATION = "$.analytics";

  public static final String ONBOARDING_CONFIGURATION = "$.onboarding";

  //  This isn't strictly part of the configuration service, but is part of how Optimize is configured
  public static final String CLOUD_PROFILE = "cloud";
  public static final String CCSM_PROFILE = "ccsm";
  public static final String PLATFORM_PROFILE = "platform";
  //@formatter:on

}
