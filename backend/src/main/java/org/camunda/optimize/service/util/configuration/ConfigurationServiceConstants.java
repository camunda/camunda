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


  String MAX_JOB_QUEUE_SIZE = "$.import.jobQueueMaxSize";
  String IMPORT_EXECUTOR_THREAD_COUNT = "$.import.executorThreadCount";
  String NUMBER_OF_RETRIES_ON_CONFLICT = "$.import.writer.numberOfRetries";
  String PROCESS_DEFINITION_IDS_TO_IMPORT = "$.import.process-definition-ids-to-import";

  String ENGINE_IMPORT_MAX_PAGE_SIZE = "$.import.pageMaxSize";
  String ENGINE_IMPORT_PROCESS_DEFINITION_MAX_PAGE_SIZE = "$.import.data.process-definition.pageSize.max";
  String ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE = "$.import.data.process-definition-xml.pageSize.max";
  String ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE = "$.import.data.activity-instance.pageSize.max";
  String ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE = "$.import.data.process-instance.pageSize.max";
  String ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE = "$.import.data.variable.pageSize.max";

  String ENGINE_IMPORT_PROCESSDEFINITION_MIN_PAGE_SIZE = "$.import.data.process-definition.pageSize.min";
  String ENGINE_IMPORT_PROCESS_DEFINITION_XML_MIN_PAGE_SIZE = "$.import.data.process-definition-xml.pageSize.min";
  String ENGINE_IMPORT_ACTIVITY_INSTANCE_MIN_PAGE_SIZE = "$.import.data.activity-instance.pageSize.min";


  String VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES = "$.plugin.variableImport.basePackages";
  String ELASTIC_SEARCH_HOST = "$.es.host";
  String ELASTIC_SEARCH_PORT = "$.es.port";
  String ELASTIC_SEARCH_CONNECTION_TIMEOUT = "$.es.connection.timeout";
  String ELASTIC_SEARCH_SCROLL_TIMEOUT = "$.es.scrollTimeout";
  String SAMPLER_INTERVAL = "$.es.sampler.interval";

  String OPTIMIZE_INDEX = "$.es.index";
  String EVENT_TYPE = "$.import.data.activity-instance.elasticsearchType";
  String PROCESS_INSTANCE_TYPE = "$.import.data.process-instance.elasticsearchType";
  String PROCESS_INSTANCE_ID_TRACKING_TYPE = "$.import.data.process-instance.idTrackingType";
  String VARIABLE_TYPE = "$.import.data.variable.elasticsearchType";
  String DURATION_HEATMAP_TARGET_VALUE_TYPE = "$.es.heatmap.duration.targetValueType";
  String PROCESS_DEFINITION_TYPE = "$.import.data.process-definition.elasticsearchType";
  String PROCESS_DEFINITION_XML_TYPE = "$.import.data.process-definition-xml.elasticsearchType";
  String ELASTIC_SEARCH_USERS_TYPE = "$.es.users.type";
  String IMPORT_INDEX_TYPE = "$.import.indexType";
  String PROCESS_DEFINITION_IMPORT_INDEX_TYPE = "$.import.data.process-definition.indexType";
  String LICENSE_TYPE = "$.es.licenseType";

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
  String IMPORT_REST_INTERVAL_MS = "$.import.handler.pages.resetInterval.value";
  String IMPORT_RESET_INTERVAL_UNIT = "$.import.handler.pages.resetInterval.unit";
  String MAXIMUM_BACK_OFF = "$.import.handler.backoff.max";
  String ES_REFRESH_INTERVAL = "$.es.settings.index.refresh_interval";
  String ES_NUMBER_OF_REPLICAS = "$.es.settings.index.number_of_replicas";
  String ES_NUMBER_OF_SHARDS = "$.es.settings.index.number_of_shards";
  String GENERAL_BACKOFF = "$.import.handler.backoff.value";

  String DATE_FORMAT = "$.serialization.dateFormat";
  String MAX_VARIABLE_VALUE_LIST_SIZE = "$.variable.maxValueListSize";
  String CONTAINER_HOST = "$.container.host";
  String CONTAINER_KEYSTORE_PASSWORD = "$.container.keystore.password";
  String CONTAINER_KEYSTORE_LOCATION = "$.container.keystore.location";
  String CONTAINER_HTTPS_PORT = "$.container.ports.https";
  String CONTAINER_HTTP_PORT = "$.container.ports.http";
}