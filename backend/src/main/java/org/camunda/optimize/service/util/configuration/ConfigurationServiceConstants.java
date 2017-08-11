package org.camunda.optimize.service.util.configuration;

/**
 * This interface should contain constants only in order not to have magic
 * string inlined in the service class.
 *
 * @author Askar Akhmerov
 */
public interface ConfigurationServiceConstants {
  String LIFE_TIME = "$.auth.token.lifeMin";
  String SECRET = "$.auth.token.secret";
  String DEFAULT_USER = "$.auth.defaultAuthentication.user";
  String DEFAULT_PASSWORD = "$.auth.defaultAuthentication.password";
  String DEFAULT_USER_ENABLED = "$.auth.defaultAuthentication.creationEnabled";

  String ENGINE_AUTH_USER = "$.engine.auth.user";
  String ENGINE_AUTH_PASSWORD = "$.engine.auth.password";
  String ENGINE_AUTH_ENABLED = "$.engine.auth.enabled";
  String OPTIMIZE_ACCESS_GROUP = "$.engine.auth.accessGroup";

  String MAX_JOB_QUEUE_SIZE = "$.engine.import.jobQueueMaxSize";
  String IMPORT_EXECUTOR_THREAD_COUNT = "$.engine.import.executorThreadCount";
  String NUMBER_OF_RETRIES_ON_CONFLICT = "$.engine.import.writer.numberOfRetries";
  String PROCESS_DEFINITIONS_TO_IMPORT = "$.engine.import.process-definition-list";

  String ENGINE_IMPORT_MAX_PAGE_SIZE = "$.engine.import.pageMaxSize";
  String ENGINE_IMPORT_PROCESS_DEFINITION_MAX_PAGE_SIZE = "$.engine.import.process-definition.pageSize.max";
  String ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE = "$.engine.import.process-definition-xml.pageSize.max";
  String ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE = "$.engine.import.activity-instance.pageSize.max";
  String ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE = "$.engine.import.process-instance.pageSize.max";
  String ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE = "$.engine.import.variable.pageSize.max";

  String ENGINE_IMPORT_PROCESSDEFINITION_MIN_PAGE_SIZE = "$.engine.import.process-definition.pageSize.min";
  String ENGINE_IMPORT_PROCESS_DEFINITION_XML_MIN_PAGE_SIZE = "$.engine.import.process-definition-xml.pageSize.min";
  String ENGINE_IMPORT_ACTIVITY_INSTANCE_MIN_PAGE_SIZE = "$.engine.import.activity-instance.pageSize.min";


  String VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES = "$.plugin.variableImport.basePackages";
  String ELASTIC_SEARCH_HOST = "$.es.host";
  String ELASTIC_SEARCH_PORT = "$.es.port";
  String ELASTIC_SEARCH_CONNECTION_TIMEOUT = "$.es.connection.timeout";
  String ELASTIC_SEARCH_SCROLL_TIMEOUT = "$.es.scrollTimeout";
  String SAMPLER_INTERVAL = "$.es.sampler.interval";

  String OPTIMIZE_INDEX = "$.es.index";
  String EVENT_TYPE = "$.es.event.type";
  String PROCESS_INSTANCE_TYPE = "$.es.processInstance.type";
  String PROCESS_INSTANCE_ID_TRACKING_TYPE = "$.es.processInstance.idTrackingType";
  String VARIABLE_TYPE = "$.es.variable.type";
  String DURATION_HEATMAP_TARGET_VALUE_TYPE = "$.es.heatmap.duration.targetValueType";
  String PROCESS_DEFINITION_TYPE = "$.es.procdef.type";
  String PROCESS_DEFINITION_XML_TYPE = "$.es.procdef.xmlType";
  String ELASTIC_SEARCH_USERS_TYPE = "$.es.users.type";
  String IMPORT_INDEX_TYPE = "$.es.import.indexType";
  String PROCESS_DEFINITION_IMPORT_INDEX_TYPE = "$.es.procdef.indexType";
  String LICENSE_TYPE = "$.es.licenseType";

  String ENGINE_REST_API_ENDPOINT = "$.engine.rest";
  String ENGINE_CONNECT_TIMEOUT = "$.engine.connection.timeout";
  String ENGINE_READ_TIMEOUT = "$.engine.read.timeout";
  String HAI_ENDPOINT = "$.engine.hai.resource";
  String HAI_COUNT_ENDPOINT = "$.engine.hai.count";
  String HVI_ENDPOINT = "$.engine.history.variable.resource";
  String HVI_COUNT_ENDPOINT = "$.engine.history.variable.count";
  String PROCESS_DEFINITION_ENDPOINT = "$.engine.procdef.resource";
  String PROCESS_DEFINITION_COUNT_ENDPOINT = "$.engine.procdef.count";
  String PROCESS_DEFINITION_XML_ENDPOINT = "$.engine.procdef.xml";
  String HPI_ENDPOINT = "$.engine.history.procinst.resource";
  String HPI_COUNT_ENDPOINT = "$.engine.history.procinst.count";
  String USER_VALIDATION_ENDPOINT = "$.engine.user.validation.resource";
  String GET_GROUPS_ENDPOINT = "$.engine.groups.resource";
  String ENGINE_NAME = "$.engine.name";
  String ENGINE_ENABLED = "$.engine.enabled";

  String ANALYZER_NAME = "$.es.analyzer.name";
  String TOKENIZER = "$.es.analyzer.tokenizer";
  String TOKEN_FILTER = "$.es.analyzer.tokenfilter";
  String IMPORT_HANDLER_INTERVAL = "$.es.import.handler.backoff.interval";
  String IMPORT_REST_INTERVAL_MS = "$.es.import.handler.pages.resetInterval.value";
  String IMPORT_RESET_INTERVAL_UNIT = "$.es.import.handler.pages.resetInterval.unit";
  String MAXIMUM_BACK_OFF = "$.es.import.handler.backoff.max";
  String ES_REFRESH_INTERVAL = "$.es.settings.index.refresh_interval";
  String ES_NUMBER_OF_REPLICAS = "$.es.settings.index.number_of_replicas";
  String ES_NUMBER_OF_SHARDS = "$.es.settings.index.number_of_shards";
  String GENERAL_BACKOFF = "$.es.import.handler.backoff.value";

  String DATE_FORMAT = "$.serialization.dateFormat";
  String MAX_VARIABLE_VALUE_LIST_SIZE = "$.variable.maxValueListSize";
  String CONTAINER_HOST = "$.container.host";
  String CONTAINER_KEYSTORE_PASSWORD = "$.container.keystore.password";
  String CONTAINER_KEYSTORE_LOCATION = "$.container.keystore.location";
  String CONTAINER_HTTPS_PORT = "$.container.ports.https";
  String CONTAINER_HTTP_PORT = "$.container.ports.http";
}