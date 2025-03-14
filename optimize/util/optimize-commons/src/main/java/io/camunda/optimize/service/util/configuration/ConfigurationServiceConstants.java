/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.List;

public final class ConfigurationServiceConstants {

  public static final String SECURITY = "$.security";
  public static final String ELASTICSEARCH = "$.es";
  public static final String OPENSEARCH = "$.opensearch";
  public static final String USERS = "$.users";
  public static final String CONFIGURED_ENGINES = "$.engines";
  public static final String CONFIGURED_ZEEBE = "$.zeebe";

  public static final String QUARTZ_JOB_STORE_CLASS = "$.alerting.quartz.jobStore";

  public static final String EMAIL_ADDRESS = "$.email.address";
  public static final String EMAIL_ENABLED = "$.email.enabled";
  public static final String EMAIL_HOSTNAME = "$.email.hostname";
  public static final String EMAIL_PORT = "$.email.port";
  public static final String EMAIL_BRANDING = "$.email.companyBranding";
  public static final String CHECK_SERVER_IDENTITY = "$.email.checkServerIdentity";

  public static final String EMAIL_AUTHENTICATION = "$.email.authentication";

  public static final String DIGEST_CRON_TRIGGER = "$.digest.cronTrigger";
  // @formatter:off
  public static final String DATABASE_MAX_JOB_QUEUE_SIZE =
      "$.import.elasticsearchJobExecutorQueueSize";
  public static final String DATABASE_IMPORT_EXECUTOR_THREAD_COUNT =
      "$.import.elasticsearchJobExecutorThreadCount";

  public static final String IMPORT_CURRENT_TIME_BACKOFF_MILLISECONDS =
      "$.import.currentTimeBackoffMilliseconds";
  public static final String IMPORT_SKIP_DATA_AFTER_NESTED_DOC_LIMIT_REACHED =
      "$.import.skipDataAfterNestedDocLimitReached";
  public static final String ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE =
      "$.import.data.process-definition-xml.maxPageSize";
  public static final String ENGINE_IMPORT_PROCESS_DEFINITION_MAX_PAGE_SIZE =
      "$.import.data.process-definition.maxPageSize";
  public static final String ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE =
      "$.import.data.activity-instance.maxPageSize";
  public static final String ENGINE_IMPORT_INCIDENT_MAX_PAGE_SIZE =
      "$.import.data.incident.maxPageSize";
  public static final String ENGINE_IMPORT_USER_TASK_INSTANCE_MAX_PAGE_SIZE =
      "$.import.data.user-task-instance.maxPageSize";
  public static final String ENGINE_IMPORT_IDENTITY_LING_LOG_MAX_PAGE_SIZE =
      "$.import.data.identity-link-log.maxPageSize";
  public static final String ENGINE_IMPORT_USER_OPERATION_LOG_MAX_PAGE_SIZE =
      "$.import.data.user-operation-log.maxPageSize";
  public static final String ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE =
      "$.import.data.process-instance.maxPageSize";
  public static final String ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE =
      "$.import.data.variable.maxPageSize";
  public static final String ENGINE_IMPORT_VARIABLE_INCLUDE_OBJECT_VARIABLE_VALUE =
      "$.import.data.variable.includeObjectVariableValue";
  public static final String ENGINE_IMPORT_DECISION_DEFINITION_MAX_PAGE_SIZE =
      "$.import.data.decision-definition.maxPageSize";
  public static final String ENGINE_IMPORT_DECISION_DEFINITION_XML_MAX_PAGE_SIZE =
      "$.import.data.decision-definition-xml.maxPageSize";
  public static final String ENGINE_IMPORT_DECISION_INSTANCE_MAX_PAGE_SIZE =
      "$.import.data.decision-instance.maxPageSize";
  public static final String ENGINE_IMPORT_TENANT_MAX_PAGE_SIZE =
      "$.import.data.tenant.maxPageSize";
  public static final String ENGINE_IMPORT_GROUP_MAX_PAGE_SIZE = "$.import.data.group.maxPageSize";
  public static final String ENGINE_IMPORT_AUTHORIZATION_MAX_PAGE_SIZE =
      "$.import.data.authorization.maxPageSize";
  public static final String IMPORT_DMN_DATA = "$.import.data.dmn.enabled";
  public static final String IMPORT_USER_TASK_WORKER_DATA =
      "$.import.data.user-task-worker.enabled";
  public static final String IMPORT_USER_TASK_IDENTITY_META_DATA =
      "$.import.data.user-task-worker.metadata";
  public static final String CUSTOMER_ONBOARDING_DATA = "$.import.customer-onboarding";

  public static final String ELASTICSEARCH_CONNECTION_TIMEOUT =
      ELASTICSEARCH + ".connection.timeout";
  public static final String ELASTICSEARCH_RESPONSE_CONSUMER_BUFFER_LIMIT_MB =
      ELASTICSEARCH + ".connection.responseConsumerBufferLimitInMb";
  public static final String ELASTICSEARCH_SCROLL_TIMEOUT_IN_SECONDS =
      ELASTICSEARCH + ".scrollTimeoutInSeconds";
  public static final String ELASTICSEARCH_CONNECTION_NODES = ELASTICSEARCH + ".connection.nodes";
  public static final String ELASTICSEARCH_PROXY = ELASTICSEARCH + ".connection.proxy";
  public static final String ELASTICSEARCH_SKIP_HOSTNAME_VERIFICATION =
      ELASTICSEARCH + ".connection.skipHostnameVerification";
  public static final String ELASTICSEARCH_PATH_PREFIX = ELASTICSEARCH + ".connection.pathPrefix";
  public static final String ELASTICSEARCH_SNAPSHOT_REPO = ELASTICSEARCH + ".backup.repositoryName";

  public static final String ELASTICSEARCH_SECURITY_USERNAME = ELASTICSEARCH + ".security.username";
  public static final String ELASTICSEARCH_SECURITY_PASSWORD = ELASTICSEARCH + ".security.password";
  public static final String ELASTICSEARCH_SECURITY_SSL_ENABLED =
      ELASTICSEARCH + ".security.ssl.enabled";
  public static final String ELASTICSEARCH_SECURITY_SSL_SELF_SIGNED =
      ELASTICSEARCH + ".security.ssl.selfSigned";
  public static final String ELASTICSEARCH_SECURITY_SSL_CERTIFICATE =
      ELASTICSEARCH + ".security.ssl.certificate";
  public static final String ELASTICSEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES =
      ELASTICSEARCH + ".security.ssl.certificate_authorities";

  public static final String OPENSEARCH_CONNECTION_NODES = OPENSEARCH + ".connection.nodes";

  public static final String OPENSEARCH_SECURITY_USERNAME = OPENSEARCH + ".security.username";
  public static final String OPENSEARCH_SECURITY_PASSWORD = OPENSEARCH + ".security.password";
  public static final String OPENSEARCH_SECURITY_SSL_ENABLED = OPENSEARCH + ".security.ssl.enabled";
  public static final String OPENSEARCH_SECURITY_SSL_SELF_SIGNED =
      OPENSEARCH + ".security.ssl.selfSigned";
  public static final String OPENSEARCH_SECURITY_SSL_CERTIFICATE =
      OPENSEARCH + ".security.ssl.certificate";
  public static final String OPENSEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES =
      OPENSEARCH + ".security.ssl.certificate_authorities";

  public static final String OPENSEARCH_AGGREGATION_BUCKET_LIMIT =
      OPENSEARCH + ".settings.aggregationBucketLimit";
  public static final String OPENSEARCH_REFRESH_INTERVAL =
      OPENSEARCH + ".settings.index.refresh_interval";
  public static final String OPENSEARCH_NUMBER_OF_REPLICAS =
      OPENSEARCH + ".settings.index.number_of_replicas";
  public static final String OPENSEARCH_NUMBER_OF_SHARDS =
      OPENSEARCH + ".settings.index.number_of_shards";
  public static final String OPENSEARCH_INDEX_NESTED_DOCUMENTS_LIMIT =
      OPENSEARCH + ".settings.index.nested_documents_limit";
  public static final String OPENSEARCH_INDEX_PREFIX = OPENSEARCH + ".settings.index.prefix";

  public static final String IMPORT_INDEX_AUTO_STORAGE_INTERVAL =
      "$.import.importIndexStorageIntervalInSec";

  public static final String INITIAL_BACKOFF_INTERVAL = "$.import.handler.backoff.initial";
  public static final String MAXIMUM_BACK_OFF = "$.import.handler.backoff.max";
  public static final String ES_AGGREGATION_BUCKET_LIMIT = "$.es.settings.aggregationBucketLimit";
  public static final String ES_REFRESH_INTERVAL = "$.es.settings.index.refresh_interval";
  public static final String ES_NUMBER_OF_REPLICAS = "$.es.settings.index.number_of_replicas";
  public static final String ES_NUMBER_OF_SHARDS = "$.es.settings.index.number_of_shards";
  public static final String ES_INDEX_PREFIX = "$.es.settings.index.prefix";
  public static final String ES_INDEX_NESTED_DOCUMENTS_LIMIT =
      "$.es.settings.index.nested_documents_limit";

  public static final String CONTAINER_HOST = "$.container.host";
  public static final String CONTAINER_CONTEXT_PATH = "$.container.contextPath";
  public static final String CONTAINER_KEYSTORE_PASSWORD = "$.container.keystore.password";
  public static final String CONTAINER_KEYSTORE_LOCATION = "$.container.keystore.location";
  public static final String CONTAINER_ENABLE_SNI_CHECK = "$.container.enableSniCheck";
  public static final String CONTAINER_HTTPS_PORT = "$.container.ports.https";
  public static final String CONTAINER_HTTP_PORT = "$.container.ports.http";
  public static final String CONTAINER_HTTP2_ENABLED = "$.container.http2Enabled";

  public static final String CONTAINER_ACTUATOR_PORT = "$.container.ports.actuator";
  public static final String CONTAINER_STATUS_MAX_CONNECTIONS =
      "$.container.status.connections.max";

  public static final String CONTAINER_ACCESS_URL = "$.container.accessUrl";
  public static final String CONTAINER_MAX_REQUEST_HEADER_IN_BYTES =
      "$.container.maxRequestHeaderSizeInBytes";
  public static final String CONTAINER_MAX_RESPONSE_HEADER_IN_BYTES =
      "$.container.maxResponseHeaderSizeInBytes";

  public static final String ENTITY_CONFIGURATION = "$.entity";

  public static final String CSV_CONFIGURATION = "$.export.csv";

  public static final String HISTORY_CLEANUP = "$.historyCleanup";
  public static final String HISTORY_CLEANUP_PROCESS_DATA = HISTORY_CLEANUP + ".processDataCleanup";

  public static final String SHARING_ENABLED = "$.sharing.enabled";

  public static final String AVAILABLE_LOCALES = "$.locales.availableLocales";
  public static final String FALLBACK_LOCALE = "$.locales.fallbackLocale";

  public static final String DATA_ARCHIVE = "$.dataArchive";

  public static final String UI_CONFIGURATION = "$.ui";

  public static final String IDENTITY_SYNC_CONFIGURATION = "$.import.identitySync";

  public static final String OPTIMIZE_API_CONFIGURATION = "$.api";

  public static final String TELEMETRY_CONFIGURATION = "$.telemetry";

  public static final String EXTERNAL_VARIABLE_CONFIGURATION = "$.externalVariable";

  public static final String CACHES_CONFIGURATION = "$.caches";

  public static final String ANALYTICS_CONFIGURATION = "$.analytics";

  public static final String ONBOARDING_CONFIGURATION = "$.onboarding";
  public static final String PANEL_NOTIFICATION_CONFIGURATION = "$.panelNotification";
  public static final String M2M_CLIENT_CONFIGURATION = "$.m2mClient";

  //  This isn't strictly part of the configuration service, but is part of how Optimize is
  // configured
  public static final String CLOUD_PROFILE = "cloud";
  public static final String CCSM_PROFILE = "ccsm";
  public static final List<String> OPTIMIZE_MODE_PROFILES = List.of(CLOUD_PROFILE, CCSM_PROFILE);

  public static final String CAMUNDA_OPTIMIZE_DATABASE = "CAMUNDA_OPTIMIZE_DATABASE";
  public static final String ELASTICSEARCH_DATABASE_PROPERTY = "elasticsearch";
  public static final String OPENSEARCH_DATABASE_PROPERTY = "opensearch";
  public static final String MULTITENANCY_ENABLED = "$.multitenancy.enabled";

  private ConfigurationServiceConstants() {}
  // @formatter:on

}
