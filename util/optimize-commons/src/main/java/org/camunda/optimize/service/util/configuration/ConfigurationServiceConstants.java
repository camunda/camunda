package org.camunda.optimize.service.util.configuration;

/**
 * This interface should contain constants only in order not to have magic
 * string inlined in the service class.
 */
public interface ConfigurationServiceConstants {
  String TOKEN_LIFE_TIME = "$.auth.token.lifeMin";

  String CONFIGURED_ENGINES = "$.engines";

  String QUARTZ_JOB_STORE_CLASS = "$.alerting.quartz.jobStore";

  String EMAIL_USERNAME = "$.email.username";
  String EMAIL_PASSWORD = "$.email.password";
  String EMAIL_ADDRESS = "$.email.address";
  String EMAIL_ENABLED = "$.email.enabled";
  String EMAIL_HOSTNAME = "$.email.hostname";
  String EMAIL_PORT = "$.email.port";
  String EMAIL_PROTOCOL = "$.email.securityProtocol";

  String ELASTICSEARCH_MAX_JOB_QUEUE_SIZE = "$.import.elasticsearchJobExecutorQueueSize";
  String ELASTICSEARCH_IMPORT_EXECUTOR_THREAD_COUNT = "$.import.elasticsearchJobExecutorThreadCount";
  String NUMBER_OF_RETRIES_ON_CONFLICT = "$.import.writer.numberOfRetries";

  String IMPORT_CURRENT_TIME_BACKOFF_MILLISECONDS = "$.import.currentTimeBackoffMilliseconds";
  String ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE = "$.import.data.process-definition-xml.maxPageSize";
  String ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE = "$.import.data.activity-instance.maxPageSize";
  String ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE = "$.import.data.process-instance.maxPageSize";
  String ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE = "$.import.data.variable.maxPageSize";
  String ENGINE_IMPORT_DECISION_DEFINITION_XML_MAX_PAGE_SIZE = "$.import.data.decision-definition-xml.maxPageSize";
  String ENGINE_IMPORT_DECISION_INSTANCE_MAX_PAGE_SIZE = "$.import.data.decision-instance.maxPageSize";

  String VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES = "$.plugin.variableImport.basePackages";
  String ENGINE_REST_FILTER_PLUGIN_BASE_PACKAGES = "$.plugin.engineRestFilter.basePackages";
  String AUTHENTICATION_EXTRACTOR_BASE_PACKAGES = "$.plugin.authenticationExtractor.basePackages";
  String ELASTIC_SEARCH_CLUSTER_NAME = "$.es.connection.clusterName";
  String ELASTIC_SEARCH_CONNECTION_TIMEOUT = "$.es.connection.timeout";
  String ELASTIC_SEARCH_SCROLL_TIMEOUT = "$.es.scrollTimeout";
  String ELASTIC_SEARCH_CONNECTION_NODES = "$.es.connection.nodes";
  String SAMPLER_INTERVAL = "$.es.connection.samplerInterval";


  String ELASTIC_SEARCH_SECURITY_USERNAME = "$.es.security.username";
  String ELASTIC_SEARCH_SECURITY_PASSWORD = "$.es.security.password";
  String ELASTIC_SEARCH_SECURITY_SSL_ENABLED = "$.es.security.ssl.enabled";
  String ELASTIC_SEARCH_SECURITY_SSL_KEY = "$.es.security.ssl.key";
  String ELASTIC_SEARCH_SECURITY_SSL_CERTIFICATE = "$.es.security.ssl.certificate";
  String ELASTIC_SEARCH_SECURITY_SSL_CERTIFICATE_AUTHORITIES = "$.es.security.ssl.certificate_authorities";
  String ELASTIC_SEARCH_SECURITY_SSL_VERIFICATION_MODE = "$.es.security.ssl.verification_mode";

  String IMPORT_INDEX_AUTO_STORAGE_INTERVAL = "$.import.importIndexStorageIntervalInSec";

  String ENGINE_CONNECT_TIMEOUT = "$.engine-commons.connection.timeout";
  String ENGINE_READ_TIMEOUT = "$.engine-commons.read.timeout";
  String PROCESS_DEFINITION_ENDPOINT = "$.engine-commons.procdef.resource";
  String PROCESS_DEFINITION_XML_ENDPOINT = "$.engine-commons.procdef.xml";
  String USER_VALIDATION_ENDPOINT = "$.engine-commons.user.validation.resource";

  String DECISION_DEFINITION_ENDPOINT = "$.engine-commons.decision-definition.resource";
  String DECISION_DEFINITION_XML_ENDPOINT = "$.engine-commons.decision-definition.xml";
  
  String IMPORT_HANDLER_INTERVAL = "$.import.handler.backoff.interval";
  String MAXIMUM_BACK_OFF = "$.import.handler.backoff.max";
  String ES_REFRESH_INTERVAL = "$.es.settings.index.refresh_interval";
  String ES_NUMBER_OF_REPLICAS = "$.es.settings.index.number_of_replicas";
  String ES_NUMBER_OF_SHARDS = "$.es.settings.index.number_of_shards";

  String ENGINE_DATE_FORMAT = "$.serialization.engineDateFormat";
  String OPTIMIZE_DATE_FORMAT = "$.serialization.optimizeDateFormat";
  String CONTAINER_HOST = "$.container.host";
  String CONTAINER_KEYSTORE_PASSWORD = "$.container.keystore.password";
  String CONTAINER_KEYSTORE_LOCATION = "$.container.keystore.location";
  String CONTAINER_HTTPS_PORT = "$.container.ports.https";
  String CONTAINER_HTTP_PORT = "$.container.ports.http";
  String CONTAINER_STATUS_MAX_CONNECTIONS = "$.container.status.connections.max";

  String CHECK_METADATA = "$.container.checkMetadata";

  String EXPORT_CSV_LIMIT = "$.export.csv.limit";
  String EXPORT_CSV_OFFSET = "$.export.csv.offset";

  String HISTORY_CLEANUP = "$.historyCleanup";

  String SHARING_ENABLED = "$.sharing.enabled";
}