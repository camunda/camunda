package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.util.ValidationHelper.ensureGreaterThanZero;

/**
 * @author Askar Akhmerov
 */
public class ConfigurationService {

  private static final String ENGINES_FIELD = "engines";
  private static final String ENGINE_REST_PATH = "/engine/";
  private final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

  private static final String[] DEFAULT_LOCATIONS = { "service-config.yaml", "environment-config.yaml" };
  private ObjectMapper objectMapper;
  private HashMap defaults = null;
  private ReadContext jsonContext;


  private Map<String, EngineConfiguration> configuredEngines;
  private Integer lifeTime;
  private String elasticSearchHost;
  private Integer elasticSearchPort;
  private String optimizeIndex;
  private String haiEndpoint;
  private String haiCountEndpoint;
  private String eventType;
  private String userValidationEndpoint;
  private String processDefinitionType;
  private String processDefinitionEndpoint;
  private String processDefinitionCountEndpoint;
  private String processDefinitionXmlType;
  private String elasticsearchUsersType;
  private String analyzerName;
  private String tokenizer;
  private String tokenFilter;
  private String defaultUser;
  private String defaultPassword;
  private String dateFormat;
  private Integer engineImportMaxPageSize;
  private Long importHandlerWait;
  private Long maximumBackoff;
  private Boolean backoffEnabled;
  private Integer elasticsearchJobExecutorQueueSize;
  private Integer elasticsearchJobExecutorThreadCount;
  private Integer engineJobExecutorQueueSize;
  private Integer engineJobExecutorThreadCount;
  private String hpiEndpoint;
  private Integer importResetIntervalValue;
  private Integer elasticsearchScrollTimeout;
  private Integer elasticsearchConnectionTimeout;
  private Integer engineConnectTimeout;
  private Integer engineReadTimeout;
  private String importIndexType;
  private String scrollImportIndexType;
  private String durationHeatmapTargetValueType;
  private String hviEndpoint;
  private String variableType;
  private Integer maxVariableValueListSize;
  private String hviCountEndpoint;
  private String processInstanceType;
  private String hpiCountEndpoint;
  private Integer engineImportProcessInstanceMaxPageSize;
  private Integer engineImportVariableInstanceMaxPageSize;
  private List<String> processDefinitionIdsToImport;
  private String processDefinitionImportIndexType;
  private String esRefreshInterval;
  private Integer esNumberOfReplicas;
  private Integer esNumberOfShards;
  private Integer engineImportProcessDefinitionXmlMaxPageSize;
  private String processDefinitionXmlEndpoint;
  private String licenseType;
  private String reportType;
  private String dashboardType;
  private Long generalBackoff;
  private Integer importIndexAutoStorageIntervalInSec;
  private Long samplerInterval;
  private List<String> variableImportPluginBasePackages;
  private String finishedPiIdTrackingType;
  private String unfinishedPiIdTrackingType;
  private String alertType;
  private Integer numberOfRetriesOnConflict;
  private Integer engineImportProcessDefinitionMaxPageSize;
  private Long engineImportActivityInstanceMaxPageSize;
  private Boolean defaultUserCreationEnabled;
  private String groupsEndpoint;
  private String importResetIntervalUnit;
  private String containerHost;
  private String containerKeystorePassword;
  private String containerKeystoreLocation;
  private Integer containerHttpsPort;
  private Integer containerHttpPort;

  public ConfigurationService() {
    this((String[]) null);
  }

  public ConfigurationService(String[] locations) {
    String[] locationsToUse = locations == null ? DEFAULT_LOCATIONS : locations;

    //prepare streams for locations
    List<InputStream> sources = new ArrayList<>();
    for (String location : locationsToUse) {
      InputStream inputStream = wrapInputStream(location);
      if (inputStream != null) {
        sources.add(inputStream);
      }
    }

    initFromStreams(sources);
  }

  public ConfigurationService(List<InputStream> sources) {
    initFromStreams(sources);
  }

  public void initFromStreams(List<InputStream> sources) {
    objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS,true);
    //read default values from the first location
    try {
      //configure Jackson as provider in order to be able to use TypeRef objects
      //during serialization process
      Configuration.setDefaults(new Configuration.Defaults() {

        private final JsonProvider jsonProvider = new JacksonJsonProvider();
        private final MappingProvider mappingProvider = new JacksonMappingProvider();

        @Override
        public JsonProvider jsonProvider() {
          return jsonProvider;
        }

        @Override
        public MappingProvider mappingProvider() {
          return mappingProvider;
        }

        @Override
        public Set<Option> options() {
          return EnumSet.noneOf(Option.class);
        }
      });

      JsonNode resultNode = objectMapper.readTree(sources.remove(0));
      //read with overriding default values all locations
      for (InputStream inputStream : sources) {
        merge(resultNode, objectMapper.readTree(inputStream));
      }

      defaults = objectMapper.convertValue(resultNode, HashMap.class);
    } catch (IOException e) {
      logger.error("error reading configuration", e);
    }

    //prepare to work with JSON Path
    jsonContext = JsonPath.parse(defaults);
  }

  public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {

    Iterator<String> fieldNames = updateNode.fieldNames();
    while (fieldNames.hasNext()) {

      String fieldName = fieldNames.next();
      JsonNode jsonNode = mainNode.get(fieldName);
      // if field exists and is an embedded object
      if (jsonNode != null && jsonNode.isObject() && !ENGINES_FIELD.equals(fieldName)) {
        merge(jsonNode, updateNode.get(fieldName));
      } else if (jsonNode != null && jsonNode.isObject() && ENGINES_FIELD.equals(fieldName)) {
        // Overwrite field
        overwriteField((ObjectNode) mainNode, updateNode, fieldName);
      } else if (mainNode instanceof ObjectNode) {
        // Overwrite field
        overwriteField((ObjectNode) mainNode, updateNode, fieldName);
      }

    }

    return mainNode;
  }

  private static void overwriteField(ObjectNode mainNode, JsonNode updateNode, String fieldName) {
    JsonNode value = updateNode.get(fieldName);
    mainNode.put(fieldName, value);
  }

  private InputStream wrapInputStream(String location) {
    return this.getClass().getClassLoader().getResourceAsStream(location);
  }

  public Map<String, EngineConfiguration> getConfiguredEngines() {
    if (configuredEngines == null) {
      TypeRef<HashMap<String, EngineConfiguration>> typeRef = new TypeRef<HashMap<String, EngineConfiguration>>() {};
      configuredEngines = jsonContext.read(ConfigurationServiceConstants.CONFIGURED_ENGINES, typeRef);
    }
    return configuredEngines;
  }

  public Integer getLifetime() {
    if(lifeTime == null) {
      lifeTime = jsonContext.read(ConfigurationServiceConstants.LIFE_TIME);
    }
    return lifeTime;
  }

  public String getElasticSearchHost() {
    if (elasticSearchHost == null) {
      elasticSearchHost = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_HOST);
    }
    return elasticSearchHost;
  }

  public Integer getElasticSearchPort() {
    if (elasticSearchPort == null) {
      elasticSearchPort = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_PORT);
    }
    return elasticSearchPort;
  }

  protected String getOptimizeIndex() {
    if (optimizeIndex == null) {
      optimizeIndex = jsonContext.read(ConfigurationServiceConstants.OPTIMIZE_INDEX);
    }
    return optimizeIndex;
  }

  public String getOptimizeIndex(String type) {
    String original = this.getOptimizeIndex() + "-" + type;
    return original.toLowerCase();
  }

  public String[] getOptimizeIndex(ArrayList<String> types) {
    String[] result = new String[types.size()];
    int i = 0;
    for (String type : types) {
      result[i] = this.getOptimizeIndex(type);
      i = i + 1;
    }
    return result;
  }

  public String getHistoricActivityInstanceEndpoint() {
    if (haiEndpoint == null) {
      haiEndpoint = jsonContext.read(ConfigurationServiceConstants.HAI_ENDPOINT);
    }
    return haiEndpoint;
  }

  public String getHistoricActivityInstanceCountEndpoint() {
    if(haiCountEndpoint == null) {
      haiCountEndpoint = jsonContext.read(ConfigurationServiceConstants.HAI_COUNT_ENDPOINT);
    }
    return haiCountEndpoint;
  }

  public String getEventType() {
    if (eventType == null) {
      eventType = jsonContext.read(ConfigurationServiceConstants.EVENT_TYPE);
    }
    return eventType;
  }

  public String getUserValidationEndpoint() {
    if (userValidationEndpoint == null) {
      userValidationEndpoint = jsonContext.read(ConfigurationServiceConstants.USER_VALIDATION_ENDPOINT);
    }
    return userValidationEndpoint;
  }

  public String getProcessDefinitionType() {
    if (processDefinitionType == null) {
      processDefinitionType = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_TYPE);
    }
    return processDefinitionType;
  }

  public String getProcessDefinitionEndpoint() {
    if (processDefinitionEndpoint == null) {
      processDefinitionEndpoint = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_ENDPOINT);
    }
    return processDefinitionEndpoint;
  }

  public String getProcessDefinitionCountEndpoint() {
    if (processDefinitionCountEndpoint == null) {
      processDefinitionCountEndpoint = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_COUNT_ENDPOINT);
    }
    return processDefinitionCountEndpoint;
  }

  public String getProcessDefinitionXmlType() {
    if (processDefinitionXmlType == null) {
      processDefinitionXmlType = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_XML_TYPE);
    }
    return processDefinitionXmlType;
  }

  public String getElasticSearchUsersType() {
    if (elasticsearchUsersType == null) {
      elasticsearchUsersType = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_USERS_TYPE);
    }
    return elasticsearchUsersType;
  }

  public String getAnalyzerName() {
    if (analyzerName == null) {
      analyzerName = jsonContext.read(ConfigurationServiceConstants.ANALYZER_NAME);
    }
    return analyzerName;
  }

  public String getTokenizer() {
    if (tokenizer == null) {
      tokenizer = jsonContext.read(ConfigurationServiceConstants.TOKENIZER);
    }
    return tokenizer;
  }

  public String getTokenFilter() {
    if (tokenFilter == null) {
      tokenFilter = jsonContext.read(ConfigurationServiceConstants.TOKEN_FILTER);
    }
    return tokenFilter;
  }

  public String getDefaultUser() {
    if (defaultUser == null) {
      defaultUser = jsonContext.read(ConfigurationServiceConstants.DEFAULT_USER);
    }
    return defaultUser;
  }

  public String getDefaultPassword() {
    if (defaultPassword == null) {
      defaultPassword = jsonContext.read(ConfigurationServiceConstants.DEFAULT_PASSWORD);
    }
    return defaultPassword;
  }

  public String getDateFormat() {
    if (dateFormat == null) {
      dateFormat = jsonContext.read(ConfigurationServiceConstants.DATE_FORMAT);
    }
    return dateFormat;
  }

  public int getImportIndexAutoStorageIntervalInSec() {
    if (importIndexAutoStorageIntervalInSec == null) {
      importIndexAutoStorageIntervalInSec =
        jsonContext.read(ConfigurationServiceConstants.IMPORT_INDEX_AUTO_STORAGE_INTERVAL, Integer.class);
    }
    return importIndexAutoStorageIntervalInSec;
  }

  public int getEngineImportMaxPageSize() {
    if (engineImportMaxPageSize == null) {
      engineImportMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_MAX_PAGE_SIZE, Integer.class);
    }
    ensureGreaterThanZero(engineImportMaxPageSize);
    return engineImportMaxPageSize;
  }

  public long getImportHandlerWait() {
    if (importHandlerWait == null) {
      importHandlerWait = jsonContext.read(ConfigurationServiceConstants.IMPORT_HANDLER_INTERVAL, Long.class);
    }
    return importHandlerWait;
  }

  public long getMaximumBackoff() {
    if (maximumBackoff == null) {
      maximumBackoff = jsonContext.read(ConfigurationServiceConstants.MAXIMUM_BACK_OFF, Long.class);
    }
    return maximumBackoff;
  }

  public Boolean isBackoffEnabled() {
    if (backoffEnabled == null) {
      backoffEnabled = jsonContext.read(ConfigurationServiceConstants.IS_BACK_OFF_ENABLED, Boolean.class);
    }
    return backoffEnabled;
  }

  public int getElasticsearchJobExecutorQueueSize() {
    if (elasticsearchJobExecutorQueueSize == null) {
      elasticsearchJobExecutorQueueSize = jsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_MAX_JOB_QUEUE_SIZE, Integer.class);
    }
    return elasticsearchJobExecutorQueueSize;
  }

  public int getEngineJobExecutorQueueSize() {
    if (engineJobExecutorQueueSize == null) {
      engineJobExecutorQueueSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_MAX_JOB_QUEUE_SIZE, Integer.class);
    }
    return engineJobExecutorQueueSize;
  }

  public int getElasticsearchJobExecutorThreadCount() {
    if (elasticsearchJobExecutorThreadCount == null) {
      elasticsearchJobExecutorThreadCount = jsonContext.read(ConfigurationServiceConstants.ELASTICSEARCH_IMPORT_EXECUTOR_THREAD_COUNT, Integer.class);
    }
    return elasticsearchJobExecutorThreadCount;
  }

  public int getEngineJobExecutorThreadCount() {
    if (engineJobExecutorThreadCount == null) {
      engineJobExecutorThreadCount = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_EXECUTOR_THREAD_COUNT, Integer.class);
    }
    return engineJobExecutorThreadCount;
  }

  public String getHistoricProcessInstanceEndpoint() {
    if (hpiEndpoint == null) {
      hpiEndpoint = jsonContext.read(ConfigurationServiceConstants.HPI_ENDPOINT);
    }
    return hpiEndpoint;
  }

  public int getElasticsearchScrollTimeout() {
    if (elasticsearchScrollTimeout == null) {
      elasticsearchScrollTimeout = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_SCROLL_TIMEOUT, Integer.class);
    }
    return elasticsearchScrollTimeout;
  }

  public int getElasticsearchConnectionTimeout() {
    if (elasticsearchConnectionTimeout == null) {
      elasticsearchConnectionTimeout = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_CONNECTION_TIMEOUT, Integer.class);
    }
    return elasticsearchConnectionTimeout;
  }

  public int getEngineConnectTimeout() {
    if (engineConnectTimeout == null) {
      engineConnectTimeout = jsonContext.read(ConfigurationServiceConstants.ENGINE_CONNECT_TIMEOUT, Integer.class);
    }
    return engineConnectTimeout;
  }

  public int getEngineReadTimeout() {
    if (engineReadTimeout == null) {
      engineReadTimeout = jsonContext.read(ConfigurationServiceConstants.ENGINE_READ_TIMEOUT, Integer.class);
    }
    return engineReadTimeout;
  }

  public String getImportIndexType() {
    if (importIndexType == null) {
      importIndexType = jsonContext.read(ConfigurationServiceConstants.IMPORT_INDEX_TYPE);
    }
    return importIndexType;
  }

  public String getScrollImportIndexType() {
    if (scrollImportIndexType == null) {
      scrollImportIndexType = jsonContext.read(ConfigurationServiceConstants.SCROLL_IMPORT_INDEX_TYPE);
    }
    return scrollImportIndexType;
  }

  public String getDurationHeatmapTargetValueType() {
    if (durationHeatmapTargetValueType == null) {
      durationHeatmapTargetValueType = jsonContext.read(ConfigurationServiceConstants.DURATION_HEATMAP_TARGET_VALUE_TYPE);
    }
    return durationHeatmapTargetValueType;
  }

  public String getHistoricVariableInstanceEndpoint() {
    if (hviEndpoint == null) {
      hviEndpoint = jsonContext.read(ConfigurationServiceConstants.HVI_ENDPOINT);
    }
    return hviEndpoint;
  }

  public String getVariableType() {
    if (variableType == null) {
      variableType = jsonContext.read(ConfigurationServiceConstants.VARIABLE_TYPE);
    }
    return variableType;
  }

  public int getMaxVariableValueListSize() {
    if (maxVariableValueListSize == null) {
      maxVariableValueListSize = jsonContext.read(ConfigurationServiceConstants.MAX_VARIABLE_VALUE_LIST_SIZE);
    }
    return maxVariableValueListSize;
  }

  public String getHistoricVariableInstanceCountEndpoint() {
    if (hviCountEndpoint == null) {
      hviCountEndpoint = jsonContext.read(ConfigurationServiceConstants.HVI_COUNT_ENDPOINT);
    }
    return hviCountEndpoint;
  }

  public String getProcessInstanceType() {
    if (processInstanceType == null) {
      processInstanceType = jsonContext.read(ConfigurationServiceConstants.PROCESS_INSTANCE_TYPE);
    }
    return processInstanceType;
  }

  public String getHistoricProcessInstanceCountEndpoint() {
    if (hpiCountEndpoint == null) {
      hpiCountEndpoint = jsonContext.read(ConfigurationServiceConstants.HPI_COUNT_ENDPOINT);
    }
    return hpiCountEndpoint;
  }

  public int getEngineImportProcessInstanceMaxPageSize() {
    if (engineImportProcessInstanceMaxPageSize == null) {
      engineImportProcessInstanceMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_INSTANCE_MAX_PAGE_SIZE);
    }
    ensureGreaterThanZero(engineImportProcessInstanceMaxPageSize);
    return engineImportProcessInstanceMaxPageSize;
  }

  public int getEngineImportVariableInstanceMaxPageSize() {
    if (engineImportVariableInstanceMaxPageSize == null) {
      engineImportVariableInstanceMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_VARIABLE_INSTANCE_MAX_PAGE_SIZE);
    }
    ensureGreaterThanZero(engineImportVariableInstanceMaxPageSize);
    return engineImportVariableInstanceMaxPageSize;
  }

  public List<String> getProcessDefinitionIdsToImport() {
    if (processDefinitionIdsToImport == null) {
      TypeRef<List<String>> typeRef = new TypeRef<List<String>>() {};
      processDefinitionIdsToImport =
        jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_IDS_TO_IMPORT, typeRef);
    }
    return processDefinitionIdsToImport;
  }

  public String getProcessDefinitionImportIndexType() {
    if (processDefinitionImportIndexType == null) {
      processDefinitionImportIndexType = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_IMPORT_INDEX_TYPE);
    }
    return processDefinitionImportIndexType;
  }

  public String getEsRefreshInterval() {
    if (esRefreshInterval == null) {
      esRefreshInterval = jsonContext.read(ConfigurationServiceConstants.ES_REFRESH_INTERVAL);
    }
    return esRefreshInterval;
  }

  public int getEsNumberOfReplicas() {
    if (esNumberOfReplicas == null) {
      esNumberOfReplicas = jsonContext.read(ConfigurationServiceConstants.ES_NUMBER_OF_REPLICAS);
    }
    return esNumberOfReplicas;
  }

  public int getEsNumberOfShards() {
    if (esNumberOfShards == null) {
      esNumberOfShards = jsonContext.read(ConfigurationServiceConstants.ES_NUMBER_OF_SHARDS);
    }
    return esNumberOfShards;
  }


  public int getEngineImportProcessDefinitionXmlMaxPageSize() {
    if (engineImportProcessDefinitionXmlMaxPageSize == null) {
      engineImportProcessDefinitionXmlMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_XML_MAX_PAGE_SIZE);
    }
    return engineImportProcessDefinitionXmlMaxPageSize;
  }

  public String getProcessDefinitionXmlEndpoint() {
    if (processDefinitionXmlEndpoint == null) {
      processDefinitionXmlEndpoint = jsonContext.read(ConfigurationServiceConstants.PROCESS_DEFINITION_XML_ENDPOINT);
    }
    return processDefinitionXmlEndpoint;
  }

  public String getLicenseType() {
    if (licenseType == null) {
      licenseType = jsonContext.read(ConfigurationServiceConstants.LICENSE_TYPE);
    }
    return licenseType;
  }

  public String getReportType() {
    if (reportType == null) {
      reportType = jsonContext.read(ConfigurationServiceConstants.REPORT_TYPE);
    }
    return reportType;
  }

  public String getDashboardType() {
    if (dashboardType == null) {
      dashboardType = jsonContext.read(ConfigurationServiceConstants.DASHBOARD_TYPE);
    }
    return dashboardType;
  }

  public long getGeneralBackoff() {
    if (generalBackoff == null) {
      generalBackoff = jsonContext.read(ConfigurationServiceConstants.GENERAL_BACKOFF, Long.class);
    }
    return generalBackoff;
  }

  public long getSamplerInterval() {
    if (samplerInterval == null) {
      samplerInterval = jsonContext.read(ConfigurationServiceConstants.SAMPLER_INTERVAL, Long.class);
    }
    return samplerInterval;
  }

  public List<String> getVariableImportPluginBasePackages() {
    if (variableImportPluginBasePackages == null) {
      TypeRef<List<String>> typeRef = new TypeRef<List<String>>() {};
      variableImportPluginBasePackages =
        jsonContext.read(ConfigurationServiceConstants.VARIABLE_IMPORT_PLUGIN_BASE_PACKAGES, typeRef);
    }
    return variableImportPluginBasePackages;
  }

  public String getFinishedProcessInstanceIdTrackingType() {
    if (finishedPiIdTrackingType == null) {
      finishedPiIdTrackingType = jsonContext.read(ConfigurationServiceConstants.FINISHED_PROCESS_INSTANCE_ID_TRACKING_TYPE);
    }
    return finishedPiIdTrackingType;
  }

  public String getUnfinishedProcessInstanceIdTrackingType() {
    if (unfinishedPiIdTrackingType == null) {
      unfinishedPiIdTrackingType = jsonContext.read(ConfigurationServiceConstants.UNFINISHED_PROCESS_INSTANCE_ID_TRACKING_TYPE);
    }
    return unfinishedPiIdTrackingType;
  }

  public int getNumberOfRetriesOnConflict() {
    if (numberOfRetriesOnConflict == null) {
      numberOfRetriesOnConflict = jsonContext.read(ConfigurationServiceConstants.NUMBER_OF_RETRIES_ON_CONFLICT);
    }
    return numberOfRetriesOnConflict;
  }

  public int getEngineImportProcessDefinitionMaxPageSize() {
    if (engineImportProcessDefinitionMaxPageSize == null) {
      engineImportProcessDefinitionMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_PROCESS_DEFINITION_MAX_PAGE_SIZE);
    }
    ensureGreaterThanZero(engineImportProcessDefinitionMaxPageSize);
    return engineImportProcessDefinitionMaxPageSize;
  }

  public long getEngineImportActivityInstanceMaxPageSize() {
    if (engineImportActivityInstanceMaxPageSize == null) {
      engineImportActivityInstanceMaxPageSize = jsonContext.read(ConfigurationServiceConstants.ENGINE_IMPORT_ACTIVITY_INSTANCE_MAX_PAGE_SIZE, Long.class);
    }
    ensureGreaterThanZero(engineImportActivityInstanceMaxPageSize);
    return engineImportActivityInstanceMaxPageSize;
  }

  public boolean isDefaultUserCreationEnabled() {
    if (defaultUserCreationEnabled == null) {
      defaultUserCreationEnabled = jsonContext.read(ConfigurationServiceConstants.DEFAULT_USER_ENABLED);
    }
    return defaultUserCreationEnabled;
  }

  public String getGetGroupsEndpoint() {
    if (groupsEndpoint == null) {
      groupsEndpoint = jsonContext.read(ConfigurationServiceConstants.GET_GROUPS_ENDPOINT);
    }
    return groupsEndpoint;
  }

  public String getImportResetIntervalUnit() {
    if (importResetIntervalUnit == null) {
      importResetIntervalUnit = jsonContext.read(ConfigurationServiceConstants.IMPORT_RESET_INTERVAL_UNIT);
    }
    return importResetIntervalUnit;
  }

  public String getContainerHost() {
    if (containerHost == null) {
      containerHost = jsonContext.read(ConfigurationServiceConstants.CONTAINER_HOST);
    }
    return containerHost;
  }

  public String getContainerKeystorePassword() {
    if (containerKeystorePassword == null) {
      containerKeystorePassword = jsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_PASSWORD);
    }
    return containerKeystorePassword;
  }

  public String getContainerKeystoreLocation() {
    if (containerKeystoreLocation == null) {
      containerKeystoreLocation = jsonContext.read(ConfigurationServiceConstants.CONTAINER_KEYSTORE_LOCATION);
    }
    return containerKeystoreLocation;
  }

  public int getContainerHttpsPort() {
    if (containerHttpsPort == null) {
      containerHttpsPort = jsonContext.read(ConfigurationServiceConstants.CONTAINER_HTTPS_PORT);
    }
    return containerHttpsPort;
  }

  public int getContainerHttpPort() {
    if (containerHttpPort == null) {
      containerHttpPort = jsonContext.read(ConfigurationServiceConstants.CONTAINER_HTTP_PORT);
    }
    return containerHttpPort;
  }

  public String getHpiEndpoint() {
    if (hpiEndpoint == null) {
      hpiEndpoint = jsonContext.read(ConfigurationServiceConstants.HPI_ENDPOINT);
    }
    return hpiEndpoint;
  }

  public String getHviEndpoint() {
    if (hviEndpoint == null) {
      hviEndpoint = jsonContext.read(ConfigurationServiceConstants.HVI_ENDPOINT);
    }
    return hviEndpoint;
  }

  public String getHaiEndpoint() {
    if (haiEndpoint == null) {
      haiEndpoint = jsonContext.read(ConfigurationServiceConstants.HAI_ENDPOINT);
    }
    return haiEndpoint;
  }

  public String getHaiCountEndpoint() {
    if (haiCountEndpoint == null) {
      haiCountEndpoint = jsonContext.read(ConfigurationServiceConstants.HAI_COUNT_ENDPOINT);
    }
    return haiCountEndpoint;
  }

  public Integer getLifeTime() {
    if (lifeTime == null) {
      lifeTime = jsonContext.read(ConfigurationServiceConstants.LIFE_TIME);
    }
    return lifeTime;
  }

  public String getElasticsearchUsersType() {
    if (elasticsearchUsersType == null) {
      elasticsearchUsersType = jsonContext.read(ConfigurationServiceConstants.ELASTIC_SEARCH_USERS_TYPE);
    }
    return elasticsearchUsersType;
  }

  public String getFinishedPiIdTrackingType() {
    if (finishedPiIdTrackingType == null) {
      finishedPiIdTrackingType = jsonContext.read(ConfigurationServiceConstants.FINISHED_PROCESS_INSTANCE_ID_TRACKING_TYPE);
    }
    return finishedPiIdTrackingType;
  }

  public String getAlertType() {
    if (alertType == null) {
      alertType = jsonContext.read(ConfigurationServiceConstants.ALERT_TYPE);
    }
    return alertType;
  }

  public Integer getImportResetIntervalValue() {
    if (importResetIntervalValue == null) {
      importResetIntervalValue = jsonContext.read(ConfigurationServiceConstants.IMPORT_RESET_INTERVAL_VALUE);
    }
    return importResetIntervalValue;
  }

  public String getHviCountEndpoint() {
    if (hviCountEndpoint == null) {
      hviCountEndpoint = jsonContext.read(ConfigurationServiceConstants.HVI_COUNT_ENDPOINT);
    }
    return hviCountEndpoint;
  }

  public String getHpiCountEndpoint() {
    if (hpiCountEndpoint == null) {
      hpiCountEndpoint = jsonContext.read(ConfigurationServiceConstants.HPI_COUNT_ENDPOINT);
    }
    return hpiCountEndpoint;
  }

  public Boolean getDefaultUserCreationEnabled() {
    if (defaultUserCreationEnabled == null) {
      defaultUserCreationEnabled = jsonContext.read(ConfigurationServiceConstants.DEFAULT_USER_ENABLED);
    }
    return defaultUserCreationEnabled;
  }

  public String getGroupsEndpoint() {
    if (groupsEndpoint == null) {
      groupsEndpoint = jsonContext.read(ConfigurationServiceConstants.GET_GROUPS_ENDPOINT);
    }
    return groupsEndpoint;
  }


  public String getProcessDefinitionXmlEndpoint(String processDefinitionId) {
    String processDefinitionXmlEndpoint =
        getProcessDefinitionEndpoint() + "/" + processDefinitionId + getProcessDefinitionXmlEndpoint();
    return processDefinitionXmlEndpoint;
  }

  public boolean isAuthorizationCheckNecessary(String engineAlias) {
    return !getOptimizeAccessGroupId(engineAlias).trim().isEmpty();
  }

  public String getEngineRestApiEndpointOfCustomEngine(String engineAlias) {
    return this.getEngineRestApiEndpoint(engineAlias) + ENGINE_REST_PATH + getEngineName(engineAlias);
  }

  public boolean isEngineAuthenticationEnabled(String engineAlias) {
    return getEngineConfiguration(engineAlias).getAuthentication().isEnabled();
  }

  public String getDefaultEngineAuthenticationUser(String engineAlias) {
    return getEngineConfiguration(engineAlias).getAuthentication().getUser();
  }

  public String getDefaultEngineAuthenticationPassword(String engineAlias) {
    return getEngineConfiguration(engineAlias).getAuthentication().getPassword();
  }

  public String getOptimizeAccessGroupId(String engineAlias) {
    return getEngineConfiguration(engineAlias).getAuthentication().getAccessGroup();
  }

  public boolean isEngineConnected(String engineAlias) {
    return getEngineConfiguration(engineAlias).isEnabled();
  }

  /**
   * This method is mostly for internal usage. All API invocations
   * should rely on {@link org.camunda.optimize.service.util.configuration.ConfigurationService#getEngineRestApiEndpointOfCustomEngine(java.lang.String)}
   *
   * @param engineAlias - an alias of configured engine
   * @return <b>raw</b> REST endpoint, without engine suffix
   */
  public String getEngineRestApiEndpoint(String engineAlias) {
    return getEngineConfiguration(engineAlias).getRest();
  }

  public String getEngineName(String engineAlias) {
    return getEngineConfiguration(engineAlias).getName();
  }

  private EngineConfiguration getEngineConfiguration(String engineAlias) {
    return this.getConfiguredEngines().get(engineAlias);
  }

  public boolean areProcessDefinitionsToImportDefined() {
    return getProcessDefinitionIdsToImport() != null && !getProcessDefinitionIdsToImport().isEmpty();
  }

  public void setVariableImportPluginBasePackages(List<String> variableImportPluginBasePackages) {
    this.variableImportPluginBasePackages = variableImportPluginBasePackages;
  }

  public void setDefaultUserCreationEnabled(Boolean defaultUserCreationEnabled) {
    this.defaultUserCreationEnabled = defaultUserCreationEnabled;
  }

  public void setProcessDefinitionIdsToImport(List<String> processDefinitionIdsToImport) {
    this.processDefinitionIdsToImport = processDefinitionIdsToImport;
  }

  public void setConfiguredEngines(Map<String, EngineConfiguration> configuredEngines) {
    this.configuredEngines = configuredEngines;
  }

  public static String getEnginesField() {
    return ENGINES_FIELD;
  }

  public static String getEngineRestPath() {
    return ENGINE_REST_PATH;
  }

  public Logger getLogger() {
    return logger;
  }

  public static String[] getDefaultLocations() {
    return DEFAULT_LOCATIONS;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public HashMap getDefaults() {
    return defaults;
  }

  public void setDefaults(HashMap defaults) {
    this.defaults = defaults;
  }

  public ReadContext getJsonContext() {
    return jsonContext;
  }

  public void setJsonContext(ReadContext jsonContext) {
    this.jsonContext = jsonContext;
  }

  public void setLifeTime(Integer lifeTime) {
    this.lifeTime = lifeTime;
  }

  public void setElasticSearchHost(String elasticSearchHost) {
    this.elasticSearchHost = elasticSearchHost;
  }

  public void setElasticSearchPort(Integer elasticSearchPort) {
    this.elasticSearchPort = elasticSearchPort;
  }

  public void setOptimizeIndex(String optimizeIndex) {
    this.optimizeIndex = optimizeIndex;
  }

  public void setHaiEndpoint(String haiEndpoint) {
    this.haiEndpoint = haiEndpoint;
  }

  public void setHaiCountEndpoint(String haiCountEndpoint) {
    this.haiCountEndpoint = haiCountEndpoint;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public void setUserValidationEndpoint(String userValidationEndpoint) {
    this.userValidationEndpoint = userValidationEndpoint;
  }

  public void setProcessDefinitionType(String processDefinitionType) {
    this.processDefinitionType = processDefinitionType;
  }

  public void setProcessDefinitionEndpoint(String processDefinitionEndpoint) {
    this.processDefinitionEndpoint = processDefinitionEndpoint;
  }

  public void setProcessDefinitionCountEndpoint(String processDefinitionCountEndpoint) {
    this.processDefinitionCountEndpoint = processDefinitionCountEndpoint;
  }

  public void setProcessDefinitionXmlType(String processDefinitionXmlType) {
    this.processDefinitionXmlType = processDefinitionXmlType;
  }

  public void setElasticsearchUsersType(String elasticsearchUsersType) {
    this.elasticsearchUsersType = elasticsearchUsersType;
  }

  public void setImportIndexAutoStorageIntervalInSec(Integer importIndexAutoStorageIntervalInSec) {
    this.importIndexAutoStorageIntervalInSec = importIndexAutoStorageIntervalInSec;
  }

  public void setAnalyzerName(String analyzerName) {
    this.analyzerName = analyzerName;
  }

  public void setTokenizer(String tokenizer) {
    this.tokenizer = tokenizer;
  }

  public void setTokenFilter(String tokenFilter) {
    this.tokenFilter = tokenFilter;
  }

  public void setDefaultUser(String defaultUser) {
    this.defaultUser = defaultUser;
  }

  public void setDefaultPassword(String defaultPassword) {
    this.defaultPassword = defaultPassword;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public void setEngineImportMaxPageSize(Integer engineImportMaxPageSize) {
    this.engineImportMaxPageSize = engineImportMaxPageSize;
  }

  public void setImportHandlerWait(Long importHandlerWait) {
    this.importHandlerWait = importHandlerWait;
  }

  public void setMaximumBackoff(Long maximumBackoff) {
    this.maximumBackoff = maximumBackoff;
  }

  public void setElasticsearchJobExecutorQueueSize(Integer elasticsearchJobExecutorQueueSize) {
    this.elasticsearchJobExecutorQueueSize = elasticsearchJobExecutorQueueSize;
  }

  public void setEngineJobExecutorQueueSize(Integer engineJobExecutorQueueSize) {
    this.engineJobExecutorQueueSize = engineJobExecutorQueueSize;
  }

  public void setElasticsearchJobExecutorThreadCount(Integer elasticsearchJobExecutorThreadCount) {
    this.elasticsearchJobExecutorThreadCount = elasticsearchJobExecutorThreadCount;
  }

  public void setEngineJobExecutorThreadCount(Integer engineJobExecutorThreadCount) {
    this.engineJobExecutorThreadCount = engineJobExecutorThreadCount;
  }

  public void setHpiEndpoint(String hpiEndpoint) {
    this.hpiEndpoint = hpiEndpoint;
  }

  public void setImportResetIntervalValue(Integer importResetIntervalValue) {
    this.importResetIntervalValue = importResetIntervalValue;
  }

  public void setElasticsearchScrollTimeout(Integer elasticsearchScrollTimeout) {
    this.elasticsearchScrollTimeout = elasticsearchScrollTimeout;
  }

  public void setElasticsearchConnectionTimeout(Integer elasticsearchConnectionTimeout) {
    this.elasticsearchConnectionTimeout = elasticsearchConnectionTimeout;
  }

  public void setEngineConnectTimeout(Integer engineConnectTimeout) {
    this.engineConnectTimeout = engineConnectTimeout;
  }

  public void setEngineReadTimeout(Integer engineReadTimeout) {
    this.engineReadTimeout = engineReadTimeout;
  }

  public void setImportIndexType(String importIndexType) {
    this.importIndexType = importIndexType;
  }

  public void setScrollImportIndexType(String scrollImportIndexType) {
    this.scrollImportIndexType = scrollImportIndexType;
  }

  public void setDurationHeatmapTargetValueType(String durationHeatmapTargetValueType) {
    this.durationHeatmapTargetValueType = durationHeatmapTargetValueType;
  }

  public void setHviEndpoint(String hviEndpoint) {
    this.hviEndpoint = hviEndpoint;
  }

  public void setVariableType(String variableType) {
    this.variableType = variableType;
  }

  public void setMaxVariableValueListSize(Integer maxVariableValueListSize) {
    this.maxVariableValueListSize = maxVariableValueListSize;
  }

  public void setHviCountEndpoint(String hviCountEndpoint) {
    this.hviCountEndpoint = hviCountEndpoint;
  }

  public void setProcessInstanceType(String processInstanceType) {
    this.processInstanceType = processInstanceType;
  }

  public void setHpiCountEndpoint(String hpiCountEndpoint) {
    this.hpiCountEndpoint = hpiCountEndpoint;
  }

  public void setEngineImportProcessInstanceMaxPageSize(Integer engineImportProcessInstanceMaxPageSize) {
    this.engineImportProcessInstanceMaxPageSize = engineImportProcessInstanceMaxPageSize;
  }

  public void setEngineImportVariableInstanceMaxPageSize(Integer engineImportVariableInstanceMaxPageSize) {
    this.engineImportVariableInstanceMaxPageSize = engineImportVariableInstanceMaxPageSize;
  }

  public void setProcessDefinitionImportIndexType(String processDefinitionImportIndexType) {
    this.processDefinitionImportIndexType = processDefinitionImportIndexType;
  }

  public void setEsRefreshInterval(String esRefreshInterval) {
    this.esRefreshInterval = esRefreshInterval;
  }

  public void setEsNumberOfReplicas(Integer esNumberOfReplicas) {
    this.esNumberOfReplicas = esNumberOfReplicas;
  }

  public void setEsNumberOfShards(Integer esNumberOfShards) {
    this.esNumberOfShards = esNumberOfShards;
  }

  public void setEngineImportProcessDefinitionXmlMaxPageSize(Integer engineImportProcessDefinitionXmlMaxPageSize) {
    this.engineImportProcessDefinitionXmlMaxPageSize = engineImportProcessDefinitionXmlMaxPageSize;
  }

  public void setProcessDefinitionXmlEndpoint(String processDefinitionXmlEndpoint) {
    this.processDefinitionXmlEndpoint = processDefinitionXmlEndpoint;
  }

  public void setLicenseType(String licenseType) {
    this.licenseType = licenseType;
  }

  public void setReportType(String reportType) {
    this.reportType = reportType;
  }

  public void setDashboardType(String dashboardType) {
    this.dashboardType = dashboardType;
  }

  public void setGeneralBackoff(Long generalBackoff) {
    this.generalBackoff = generalBackoff;
  }

  public void setSamplerInterval(Long samplerInterval) {
    this.samplerInterval = samplerInterval;
  }

  public void setFinishedPiIdTrackingType(String finishedPiIdTrackingType) {
    this.finishedPiIdTrackingType = finishedPiIdTrackingType;
  }

  public void setNumberOfRetriesOnConflict(Integer numberOfRetriesOnConflict) {
    this.numberOfRetriesOnConflict = numberOfRetriesOnConflict;
  }

  public void setEngineImportProcessDefinitionMaxPageSize(Integer engineImportProcessDefinitionMaxPageSize) {
    this.engineImportProcessDefinitionMaxPageSize = engineImportProcessDefinitionMaxPageSize;
  }

  public void setEngineImportActivityInstanceMaxPageSize(Long engineImportActivityInstanceMaxPageSize) {
    this.engineImportActivityInstanceMaxPageSize = engineImportActivityInstanceMaxPageSize;
  }



  public void setGroupsEndpoint(String groupsEndpoint) {
    this.groupsEndpoint = groupsEndpoint;
  }

  public void setImportResetIntervalUnit(String importResetIntervalUnit) {
    this.importResetIntervalUnit = importResetIntervalUnit;
  }

  public void setContainerHost(String containerHost) {
    this.containerHost = containerHost;
  }

  public void setContainerKeystorePassword(String containerKeystorePassword) {
    this.containerKeystorePassword = containerKeystorePassword;
  }

  public void setContainerKeystoreLocation(String containerKeystoreLocation) {
    this.containerKeystoreLocation = containerKeystoreLocation;
  }

  public void setContainerHttpsPort(Integer containerHttpsPort) {
    this.containerHttpsPort = containerHttpsPort;
  }

  public void setContainerHttpPort(Integer containerHttpPort) {
    this.containerHttpPort = containerHttpPort;
  }

  public void setBackoffEnabled(Boolean backoffEnabled) {
    this.backoffEnabled = backoffEnabled;
  }



}
