package org.camunda.optimize.service.util.configuration;

/**
 * This interface should contain constants only in order not to have magic
 * string inlined in the service class.
 *
 * @author Askar Akhmerov
 */
public interface ConfigurationServiceConstants {
  String LIFE_TIME = "$.auth.token.lifeMin";
  String DEFAULT_USER = "$.auth.defaultAuthentication.user";
  String DEFAULT_PASSWORD = "$.auth.defaultAuthentication.password";
  String DEFAULT_USER_ENABLED = "$.auth.defaultAuthentication.creationEnabled";

  String CONFIGURED_ENGINES = "$.engines";

  String QUARTZ_JOB_STORE_CLASS = "$.alerting.quartz.jobStore";
  String EMAIL_USERNAME = "$.alerting.email.username";
  String EMAIL_PASSWORD = "$.alerting.email.password";
  String EMAIL_ADDRESS = "$.alerting.email.address";
  String EMAIL_ENABLED = "$.alerting.email.enabled";
  String EMAIL_HOSTNAME = "$.alerting.email.hostname";
  String EMAIL_PORT = "$.alerting.email.port";
  String EMAIL_PROTOCOL = "$.alerting.email.securityProtocol";

  String ELASTICSEARCH_MAX_JOB_QUEUE_SIZE = "$.import.elasticsearchJobExecutorQueueSize";
  String ELASTICSEARCH_IMPORT_EXECUTOR_THREAD_COUNT = "$.import.elasticsearchJobExecutorThreadCount";
  String NUMBER_OF_RETRIES_ON_CONFLICT = "$.import.writer.numberOfRetries";
  String PROCESS_DEFINITION_IDS_TO_IMPORT = "$.import.process-definition-ids-to-import";

  String ENGINE_IMPORT_MAX_PAGE_SIZE = "$.import.maxPageSize";
  String ENGINE_IMPORT_PROCESS_DEFINITION_MAX_PAGE_SIZE = "$.import.data.process-definition.maxPageSize";
  String ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE = "$.import.data.process-definition-xml.maxPageSize";
  String ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE = "$.import.data.activity-instance.maxPageSize";
  String ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE = "$.import.data.process-instance.maxPageSize";
  String ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE = "$.import.data.variable.maxPageSize";

  String VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES = "$.plugin.variableImport.basePackages";
  String ELASTIC_SEARCH_HOST = "$.es.host";
  String ELASTIC_SEARCH_PORT = "$.es.port";
  String ELASTIC_SEARCH_CONNECTION_TIMEOUT = "$.es.connection.timeout";
  String ELASTIC_SEARCH_SCROLL_TIMEOUT = "$.es.scrollTimeout";
  String SAMPLER_INTERVAL = "$.es.sampler.interval";

  String OPTIMIZE_INDEX = "$.es.index";
  String EVENT_TYPE = "$.import.data.activity-instance.elasticsearchType";
  String PROCESS_INSTANCE_TYPE = "$.import.data.process-instance.elasticsearchType";
  String FINISHED_PROCESS_INSTANCE_ID_TRACKING_TYPE = "$.import.data.process-instance.finishedIdTrackingType";
  String UNFINISHED_PROCESS_INSTANCE_ID_TRACKING_TYPE = "$.import.data.process-instance.unfinishedIdTrackingType";
  String VARIABLE_TYPE = "$.import.data.variable.elasticsearchType";
  String DURATION_HEATMAP_TARGET_VALUE_TYPE = "$.es.heatmap.duration.targetValueType";
  String PROCESS_DEFINITION_TYPE = "$.import.data.process-definition.elasticsearchType";
  String PROCESS_DEFINITION_XML_TYPE = "$.import.data.process-definition-xml.elasticsearchType";
  String ELASTIC_SEARCH_USERS_TYPE = "$.es.users.type";
  String IMPORT_INDEX_TYPE = "$.import.indexType";
  String SCROLL_IMPORT_INDEX_TYPE = "$.import.scrollImportIndexType";
  String IMPORT_INDEX_AUTO_STORAGE_INTERVAL = "$.import.importIndexStorageIntervalInSec";
  String PROCESS_DEFINITION_IMPORT_INDEX_TYPE = "$.import.data.process-definition.indexType";
  String LICENSE_TYPE = "$.es.licenseType";
  String REPORT_TYPE = "$.es.reportType";
  String DASHBOARD_TYPE = "$.es.dashboardType";
  String ALERT_TYPE = "$.es.alertType";
  String REPORT_SHARE_TYPE = "$.es.reportShareType";
  String DASHBOARD_SHARE_TYPE = "$.es.dashboardShareType";

  String ENGINE_CONNECT_TIMEOUT = "$.engine-commons.connection.timeout";
  String ENGINE_READ_TIMEOUT = "$.engine-commons.read.timeout";
  String HAI_ENDPOINT = "$.engine-commons.hai.resource";
  String HAI_COUNT_ENDPOINT = "$.engine-commons.hai.count";
  String HVI_ENDPOINT = "$.engine-commons.history.variable.resource";
  String HVI_COUNT_ENDPOINT = "$.engine-commons.history.variable.count";
  String PROCESS_DEFINITION_ENDPOINT = "$.engine-commons.procdef.resource";
  String PROCESS_DEFINITION_COUNT_ENDPOINT = "$.engine-commons.procdef.count";
  String PROCESS_DEFINITION_XML_ENDPOINT = "$.engine-commons.procdef.xml";
  String HPI_ENDPOINT = "$.engine-commons.history.procinst.resource";
  String HPI_COUNT_ENDPOINT = "$.engine-commons.history.procinst.count";
  String USER_VALIDATION_ENDPOINT = "$.engine-commons.user.validation.resource";
  String GET_GROUPS_ENDPOINT = "$.engine-commons.groups.resource";

  String ANALYZER_NAME = "$.es.analyzer.name";
  String TOKENIZER = "$.es.analyzer.tokenizer";
  String TOKEN_FILTER = "$.es.analyzer.tokenfilter";
  String IMPORT_HANDLER_INTERVAL = "$.import.handler.backoff.interval";
  String MAXIMUM_BACK_OFF = "$.import.handler.backoff.max";
  String IS_BACK_OFF_ENABLED = "$.import.handler.backoff.isEnabled";
  String ES_REFRESH_INTERVAL = "$.es.settings.index.refresh_interval";
  String ES_NUMBER_OF_REPLICAS = "$.es.settings.index.number_of_replicas";
  String ES_NUMBER_OF_SHARDS = "$.es.settings.index.number_of_shards";
  String GENERAL_BACKOFF = "$.import.handler.backoff.value";

  String ENGINE_DATE_FORMAT = "$.serialization.engineDateFormat";
  String OPTIMIZE_DATE_FORMAT = "$.serialization.optimizeDateFormat";
  String MAX_VARIABLE_VALUE_LIST_SIZE = "$.variable.maxValueListSize";
  String CONTAINER_HOST = "$.container.host";
  String CONTAINER_KEYSTORE_PASSWORD = "$.container.keystore.password";
  String CONTAINER_KEYSTORE_LOCATION = "$.container.keystore.location";
  String CONTAINER_HTTPS_PORT = "$.container.ports.https";
  String CONTAINER_HTTP_PORT = "$.container.ports.http";
  String CONTAINER_STATUS_MAX_CONNECTIONS = "$.container.status.connections.max";

  String EXPORT_CSV_LIMIT = "$.export.csv.limit";
  String EXPORT_CSV_OFFSET = "$.export.csv.offset";

}