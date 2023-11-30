/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension.db;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import jakarta.ws.rs.NotFoundException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import org.camunda.optimize.service.db.es.schema.index.TerminatedUserSessionIndexES;
import org.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import org.camunda.optimize.service.db.es.schema.index.events.EventIndexES;
import org.camunda.optimize.service.db.es.schema.index.events.EventSequenceCountIndexES;
import org.camunda.optimize.service.db.es.schema.index.report.SingleProcessReportIndexES;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.DatabaseProfile;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.mockserver.integration.ClientAndServer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.service.db.es.OptimizeElasticsearchClient.INDICES_EXIST_OPTIONS;
import static org.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil.mapHits;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableIdField;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

@Slf4j
public class ElasticsearchDatabaseTestService extends DatabaseTestService {

  private static final ToXContent.Params XCONTENT_PARAMS_FLAT_SETTINGS = new ToXContent.MapParams(
    Collections.singletonMap("flat_settings", "true")
  );
  private static final String MOCKSERVER_CLIENT_KEY = "MockServer";
  private static final Map<String, OptimizeElasticsearchClient> CLIENT_CACHE = new HashMap<>();
  private static final ClientAndServer mockServerClient = initMockServer();

  private final String customIndexPrefix;
  private boolean haveToClean;

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;

  public ElasticsearchDatabaseTestService(final String customIndexPrefix,
                                          final boolean haveToClean) {
    this.customIndexPrefix = customIndexPrefix;
    this.haveToClean = haveToClean;
    initEsClient();
  }

  @Override
  public DatabaseClient getDatabaseClient() {
    return prefixAwareRestHighLevelClient;
  }

  @Override
  public OptimizeElasticsearchClient getOptimizeElasticsearchClient() {
    return prefixAwareRestHighLevelClient;
  }

  public void disableCleanup() {
    this.haveToClean = false;
  }

  @Override
  public void beforeEach() {
    if (haveToClean) {
      log.info("Cleaning database...");
      cleanAndVerifyDatabase();
      log.info("All documents have been wiped out! Database has successfully been cleaned!");
    }
  }

  @Override
  public void afterEach() {
    // If the MockServer has been used, we reset all expectations and logs and revert to the default client
    if (prefixAwareRestHighLevelClient == CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY)) {
      log.info("Resetting all MockServer expectations and logs");
      mockServerClient.reset();
      log.info("No longer using ES MockServer");
      initEsClient();
    }
  }

  @Override
  public ClientAndServer useDBMockServer() {
    log.debug("Using ElasticSearch MockServer");
    if (CLIENT_CACHE.containsKey(MOCKSERVER_CLIENT_KEY)) {
      prefixAwareRestHighLevelClient = CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY);
    } else {
      final ConfigurationService configurationService = createConfigurationService();
      final DatabaseConnectionNodeConfiguration esConfig =
        configurationService.getElasticSearchConfiguration().getFirstConnectionNode();
      esConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
      esConfig.setHttpPort(mockServerClient.getLocalPort());
      createClientAndAddToCache(MOCKSERVER_CLIENT_KEY, configurationService);
    }
    return mockServerClient;
  }

  @Override
  public void refreshAllOptimizeIndices() {
    try {
      RefreshRequest refreshAllIndicesRequest = new RefreshRequest(getIndexNameService().getIndexPrefix() + "*");
      getOptimizeElasticClient().getHighLevelClient()
        .indices()
        .refresh(refreshAllIndicesRequest, getOptimizeElasticClient().requestOptions());
    } catch (Exception e) {
      throw new OptimizeIntegrationTestException("Could not refresh Optimize indices!", e);
    }
  }

  @Override
  public void addEntryToDatabase(String indexName, String id, Object entry) {
    try {
      String json = OBJECT_MAPPER.writeValueAsString(entry);
      IndexRequest request = new IndexRequest(indexName)
        .id(id)
        .source(json, XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE); // necessary in order to search for the entry immediately
      getOptimizeElasticClient().index(request);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Unable to add an entry to elasticsearch", e);
    }
  }

  @Override
  public void addEntriesToDatabase(String indexName, Map<String, Object> idToEntryMap) {
    StreamSupport.stream(Iterables.partition(idToEntryMap.entrySet(), 10_000).spliterator(), false)
      .forEach(batch -> {
        final BulkRequest bulkRequest = new BulkRequest();
        for (Map.Entry<String, Object> idAndObject : batch) {
          String json = writeJsonString(idAndObject);
          IndexRequest request = new IndexRequest(indexName)
            .id(idAndObject.getKey())
            .source(json, XContentType.JSON);
          bulkRequest.add(request);
        }
        executeBulk(bulkRequest);
      });
  }

  @Override
  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type) {
    return getAllDocumentsOfIndexAs(indexName, type, matchAllQuery());
  }

  public OptimizeIndexNameService getIndexNameService() {
    return getOptimizeElasticClient().getIndexNameService();
  }

  @Override
  public Integer getDocumentCountOf(final String indexName) {
    try {
      final CountResponse countResponse = getOptimizeElasticClient()
        .count(new CountRequest(indexName).query(matchAllQuery()));
      return Long.valueOf(countResponse.getCount()).intValue();
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate document count for index " + indexName,
        e
      );
    }
  }

  @Override
  public Integer getCountOfCompletedInstances() {
    return getInstanceCountWithQuery(boolQuery().must(existsQuery(ProcessInstanceIndex.END_DATE)));
  }

  @Override
  public Integer getCountOfCompletedInstancesWithIdsIn(final Set<Object> processInstanceIds) {
    return getInstanceCountWithQuery(
      boolQuery()
        .filter(termsQuery(ProcessInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds))
        .filter(existsQuery(ProcessInstanceIndex.END_DATE)));
  }

  @Override
  public Integer getActivityCountForAllProcessInstances() {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .fetchSource(false)
      .size(0)
      .aggregation(
        nested(FLOW_NODE_INSTANCES, FLOW_NODE_INSTANCES)
          .subAggregation(
            count(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION)
              .field(FLOW_NODE_INSTANCES + "." + ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID)
          )
      );

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest);
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException("Could not evaluate activity count in process instance indices.", e);
    }

    Nested nested = searchResponse.getAggregations()
      .get(FLOW_NODE_INSTANCES);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION);
    return Long.valueOf(countAggregator.getValue()).intValue();
  }

  @Override
  public Integer getVariableInstanceCountForAllProcessInstances() {
    return getVariableInstanceCountForAllProcessInstances(QueryBuilders.matchAllQuery());
  }

  @Override
  public Integer getVariableInstanceCountForAllCompletedProcessInstances() {
    return getVariableInstanceCountForAllProcessInstances(boolQuery().must(existsQuery(ProcessInstanceIndex.END_DATE)));
  }

  @Override
  public void deleteAllOptimizeData() {
    DeleteByQueryRequest request = new DeleteByQueryRequest(getIndexNameService().getIndexPrefix() + "*")
      .setQuery(matchAllQuery())
      .setRefresh(true);

    try {
      getOptimizeElasticClient().getHighLevelClient()
        .deleteByQuery(request, getOptimizeElasticClient().requestOptions());
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all Optimize data", e);
    }
  }

  @SneakyThrows
  @Override
  public void deleteAllIndicesContainingTerm(final String indexTerm) {
    final String[] indicesToDelete = getOptimizeElasticClient().getAllIndexNames().stream()
      .filter(index -> index.contains(indexTerm))
      .toArray(String[]::new);
    if (indicesToDelete.length > 0) {
      getOptimizeElasticClient().deleteIndexByRawIndexNames(indicesToDelete);
    }
  }

  @Override
  public void deleteAllSingleProcessReports() {
    final DeleteByQueryRequest request =
      new DeleteByQueryRequest(getIndexNameService().getOptimizeIndexAliasForIndex(new SingleProcessReportIndexES()))
        .setQuery(matchAllQuery())
        .setRefresh(true);

    try {
      getOptimizeElasticClient().getHighLevelClient()
        .deleteByQuery(request, getOptimizeElasticClient().requestOptions());
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Could not delete data in index " + getIndexNameService().getOptimizeIndexAliasForIndex(new SingleProcessReportIndexES()),
        e
      );
    }
  }

  public void deleteExternalEventSequenceCountIndex() {
    deleteIndexOfMapping(new EventSequenceCountIndexES(EXTERNAL_EVENTS_INDEX_SUFFIX));
  }

  public void deleteTerminatedSessionsIndex() {
    deleteIndexOfMapping(new TerminatedUserSessionIndexES());
  }

  @Override
  public void deleteAllVariableUpdateInstanceIndices() {
    final String[] indexNames = getOptimizeElasticClient().getAllIndicesForAlias(
      getIndexNameService().getOptimizeIndexAliasForIndex(new VariableUpdateInstanceIndexES())).toArray(String[]::new);
    getOptimizeElasticClient().deleteIndexByRawIndexNames(indexNames);
  }

  @Override
  public void deleteAllExternalVariableIndices() {
    final String[] indexNames = getOptimizeElasticClient().getAllIndicesForAlias(
      getIndexNameService().getOptimizeIndexAliasForIndex(new ExternalProcessVariableIndexES())).toArray(String[]::new);
    getOptimizeElasticClient().deleteIndexByRawIndexNames(indexNames);
  }

  @Override
  public boolean indexExists(final String indexOrAliasName) {
    final GetIndexRequest request = new GetIndexRequest(indexOrAliasName);
    try {
      return getOptimizeElasticClient().exists(request);
    } catch (IOException e) {
      final String message = String.format(
        "Could not check if [%s] index already exist.", String.join(",", indexOrAliasName)
      );
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return prefixAwareRestHighLevelClient;
  }

  @SneakyThrows
  @Override
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(final String dbType, final String engine) {
    GetRequest getRequest = new GetRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME).id(DatabaseHelper.constructKey(dbType, engine));
    GetResponse response = prefixAwareRestHighLevelClient.get(getRequest);
    if (response.isExists()) {
      return OBJECT_MAPPER.readValue(response.getSourceAsString(), TimestampBasedImportIndexDto.class)
        .getTimestampOfLastEntity();
    } else {
      throw new NotFoundException(String.format(
        "Timestamp based import index does not exist: esType: {%s}, engine: {%s}",
        dbType,
        engine
      ));
    }
  }

  @Override
  public void deleteAllExternalEventIndices() {
    final String eventIndexAlias = getIndexNameService().getOptimizeIndexAliasForIndex(new EventIndexES());
    final String[] eventIndices = getOptimizeElasticClient().getAllIndicesForAlias(eventIndexAlias).toArray(String[]::new);
    getOptimizeElasticClient().deleteIndexByRawIndexNames(eventIndices);
  }

  @SneakyThrows
  @Override
  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    final String[] indicesToDelete = Arrays.stream(
        getOptimizeElasticClient().getHighLevelClient().indices().get(
            new GetIndexRequest("*").indicesOptions(INDICES_EXIST_OPTIONS),
            getOptimizeElasticClient().requestOptions()
          )
          .getIndices())
      .filter(indexName -> indexName.contains(zeebeRecordPrefix))
      .toArray(String[]::new);
    if (indicesToDelete.length > 1) {
      getOptimizeElasticClient().deleteIndexByRawIndexNames(indicesToDelete);
    }
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsForPrefix(final String zeebeRecordPrefix, final String indexName,
                                          final String updateScript) {
    final UpdateByQueryRequest update = new UpdateByQueryRequest(zeebeRecordPrefix + "_" + indexName + "*")
      .setQuery(matchAllQuery())
      .setScript(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, updateScript, Collections.emptyMap()))
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setRefresh(true);
    getOptimizeElasticClient().getHighLevelClient().updateByQuery(update, getOptimizeElasticClient().requestOptions());
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsWithPositionForPrefix(final String zeebeRecordPrefix, final String indexName,
                                                      final long position, final String updateScript) {
    final UpdateByQueryRequest update = new UpdateByQueryRequest(zeebeRecordPrefix + "_" + indexName + "*")
      .setQuery(boolQuery().must(termQuery(ZeebeRecordDto.Fields.position, position)))
      .setScript(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, updateScript, Collections.emptyMap()))
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setRefresh(true);
    getOptimizeElasticClient().getHighLevelClient().updateByQuery(update, getOptimizeElasticClient().requestOptions());
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsOfBpmnElementTypeForPrefix(final String zeebeRecordPrefix,
                                                           final BpmnElementType bpmnElementType,
                                                           final String updateScript) {
    final UpdateByQueryRequest update = new UpdateByQueryRequest(
      zeebeRecordPrefix + "_" + ZEEBE_PROCESS_INSTANCE_INDEX_NAME + "*")
      .setQuery(
        boolQuery()
          .must(termQuery(
            ZeebeRecordDto.Fields.value + "." + ZeebeProcessInstanceDataDto.Fields.bpmnElementType,
            bpmnElementType.name()
          ))
      )
      .setScript(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, updateScript, Collections.emptyMap()))
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setRefresh(true);
    getOptimizeElasticClient().getHighLevelClient().updateByQuery(update, getOptimizeElasticClient().requestOptions());
  }

  @SneakyThrows
  @Override
  public void updateUserTaskDurations(final String processInstanceId, final String processDefinitionKey,
                                      final long duration) {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("flowNodesField", FLOW_NODE_INSTANCES)
        .put("flowNodeTypeField", FLOW_NODE_TYPE)
        .put("totalDurationField", FLOW_NODE_TOTAL_DURATION)
        .put("idleDurationField", USER_TASK_IDLE_DURATION)
        .put("workDurationField", USER_TASK_WORK_DURATION)
        .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
        .put("newDuration", String.valueOf(duration))
        .build()
    );
    // @formatter:off
    final String updateScript = substitutor.replace(
      "for (def flowNode : ctx._source.${flowNodesField}) {" +
          "if (flowNode.${flowNodeTypeField}.equals(\"${userTaskFlowNodeType}\")) {" +
            "flowNode.${totalDurationField} = ${newDuration};" +
            "flowNode.${workDurationField} = ${newDuration};" +
            "flowNode.${idleDurationField} = ${newDuration};" +
          "}" +
        "}"
    );
    // @formatter:on

    final UpdateRequest update = new UpdateRequest()
      .index(getProcessInstanceIndexAliasName(processDefinitionKey))
      .id(processInstanceId)
      .script(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, updateScript, Collections.emptyMap()))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    getOptimizeElasticClient().update(update);
  }

  @SneakyThrows
  @Override
  public void deleteIndicesStartingWithPrefix(final String term) {
    final String[] indicesToDelete = getOptimizeElasticClient().getAllIndexNames()
      .stream()
      .filter(indexName -> indexName.startsWith(getIndexNameService().getIndexPrefix() + "-" + term))
      .toArray(String[]::new);
    if (indicesToDelete.length > 0) {
      getOptimizeElasticClient().deleteIndexByRawIndexNames(indicesToDelete);
    }
  }

  private void initEsClient() {
    if (CLIENT_CACHE.containsKey(customIndexPrefix)) {
      prefixAwareRestHighLevelClient = CLIENT_CACHE.get(customIndexPrefix);
    } else {
      createClientAndAddToCache(customIndexPrefix, createConfigurationService());
    }
  }

  private static ClientAndServer initMockServer() {
    log.debug("Setting up ES MockServer on port {}", IntegrationTestConfigurationUtil.getElasticsearchMockServerPort());
    final DatabaseConnectionNodeConfiguration esConfig =
      IntegrationTestConfigurationUtil.createItConfigurationService().getElasticSearchConfiguration().getFirstConnectionNode();
    return MockServerUtil.createProxyMockServer(
      esConfig.getHost(),
      esConfig.getHttpPort(),
      IntegrationTestConfigurationUtil.getElasticsearchMockServerPort()
    );
  }

  private void createClientAndAddToCache(String clientKey, ConfigurationService configurationService) {
    final DatabaseConnectionNodeConfiguration esConfig =
      configurationService.getElasticSearchConfiguration().getFirstConnectionNode();
    log.info("Creating ES Client with host {} and port {}", esConfig.getHost(), esConfig.getHttpPort());
    prefixAwareRestHighLevelClient = new OptimizeElasticsearchClient(
      ElasticsearchHighLevelRestClientBuilder.build(configurationService),
      new OptimizeIndexNameService(configurationService, DatabaseProfile.ELASTICSEARCH)
    );
    adjustClusterSettings();
    CLIENT_CACHE.put(clientKey, prefixAwareRestHighLevelClient);
  }

  private ConfigurationService createConfigurationService() {
    final ConfigurationService configurationService = IntegrationTestConfigurationUtil.createItConfigurationService();
    if (customIndexPrefix != null) {
      configurationService.getElasticSearchConfiguration()
        .setIndexPrefix(configurationService.getElasticSearchConfiguration().getIndexPrefix() + customIndexPrefix);
    }
    return configurationService;
  }

  private void adjustClusterSettings() {
    Settings settings = Settings.builder()
      // we allow auto index creation because the Zeebe exporter creates indices for records
      .put("action.auto_create_index", true)
      // all of our tests are running against a one node cluster. Since we're creating a lot of indexes,
      // we are easily hitting the default value of 1000. Thus, we need to increase this value for the test setup.
      .put("cluster.max_shards_per_node", 10_000)
      .build();
    ClusterUpdateSettingsRequest clusterUpdateSettingsRequest = new ClusterUpdateSettingsRequest();
    clusterUpdateSettingsRequest.persistentSettings(settings);
    try (XContentBuilder builder = jsonBuilder()) {
      // low level request as we need body serialized with flat_settings option for AWS hosted elasticsearch support
      Request request = new Request("PUT", "/_cluster/settings");
      request.setJsonEntity(Strings.toString(
        clusterUpdateSettingsRequest.toXContent(builder, XCONTENT_PARAMS_FLAT_SETTINGS)
      ));
      prefixAwareRestHighLevelClient.getLowLevelClient().performRequest(request);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not update cluster settings!", e);
    }
  }

  @SneakyThrows
  private String writeJsonString(final Map.Entry<String, Object> idAndObject) {
    return OBJECT_MAPPER.writeValueAsString(idAndObject.getValue());
  }

  @SneakyThrows
  private void executeBulk(final BulkRequest bulkRequest) {
    getOptimizeElasticClient().bulk(bulkRequest);
  }

  private <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type, final QueryBuilder query) {
    try {
      return getAllDocumentsOfIndicesAs(new String[]{indexName}, type, query);
    } catch (ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot get all documents for index " + indexName,
        e
      );
    }
  }

  @SneakyThrows
  private <T> List<T> getAllDocumentsOfIndicesAs(final String[] indexNames, final Class<T> type,
                                                 final QueryBuilder query) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .trackTotalHits(true)
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(indexNames)
      .source(searchSourceBuilder);

    final SearchResponse response = getOptimizeElasticsearchClient().search(searchRequest);
    return mapHits(response.getHits(), type, getObjectMapper());
  }

  private int getInstanceCountWithQuery(final BoolQueryBuilder query) {
    try {
      final CountResponse countResponse = getOptimizeElasticClient()
        .count(new CountRequest(PROCESS_INSTANCE_MULTI_ALIAS).query(query));
      return Long.valueOf(countResponse.getCount()).intValue();
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate document count for index " + PROCESS_INSTANCE_MULTI_ALIAS,
        e
      );
    }
  }

  private Integer getVariableInstanceCountForAllProcessInstances(final QueryBuilder processInstanceQuery) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(processInstanceQuery)
      .fetchSource(false)
      .size(0);

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    searchSourceBuilder.aggregation(
      nested(VARIABLES, VARIABLES)
        .subAggregation(
          count("count")
            .field(getNestedVariableIdField())
        )
    );

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest);
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate variable instance count in process instance indices.",
        e
      );
    }

    Nested nestedAgg = searchResponse.getAggregations().get(VARIABLES);
    ValueCount countAggregator = nestedAgg.getAggregations().get("count");
    long totalVariableCount = countAggregator.getValue();

    return Long.valueOf(totalVariableCount).intValue();
  }

  private void deleteIndexOfMapping(final IndexMappingCreator<XContentBuilder> indexMapping) {
    getOptimizeElasticClient().deleteIndex(indexMapping);
  }

}
