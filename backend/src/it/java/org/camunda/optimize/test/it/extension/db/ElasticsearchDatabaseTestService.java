/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension.db;

import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.service.db.es.OptimizeElasticsearchClient.INDICES_EXIST_OPTIONS;
import static org.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil.mapHits;
import static org.camunda.optimize.service.db.es.schema.ElasticSearchIndexSettingsBuilder.buildDynamicSettings;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableIdField;
import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX;
import static org.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsWithPercentileInterpolation;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsWithoutPercentileInterpolation;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

import com.google.common.collect.Iterables;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import org.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import org.camunda.optimize.service.db.es.schema.index.TerminatedUserSessionIndexES;
import org.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import org.camunda.optimize.service.db.es.schema.index.events.EventIndexES;
import org.camunda.optimize.service.db.es.schema.index.events.EventProcessPublishStateIndexES;
import org.camunda.optimize.service.db.es.schema.index.events.EventSequenceCountIndexES;
import org.camunda.optimize.service.db.es.schema.index.report.SingleProcessReportIndexES;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.db.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.DatabaseType;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.test.repository.TestIndexRepositoryES;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.mockserver.integration.ClientAndServer;

@Slf4j
public class ElasticsearchDatabaseTestService extends DatabaseTestService {

  private static final ToXContent.Params XCONTENT_PARAMS_FLAT_SETTINGS =
      new ToXContent.MapParams(Collections.singletonMap("flat_settings", "true"));
  private static final String MOCKSERVER_CLIENT_KEY = "MockServer";
  private static final Map<String, OptimizeElasticsearchClient> CLIENT_CACHE = new HashMap<>();
  private static final ClientAndServer mockServerClient = initMockServer();

  private String elasticsearchDatabaseVersion;

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;

  public ElasticsearchDatabaseTestService(
      final String customIndexPrefix, final boolean haveToClean) {
    super(customIndexPrefix, haveToClean);
    initEsClient();
    setTestIndexRepository(new TestIndexRepositoryES(prefixAwareRestHighLevelClient));
  }

  private static ClientAndServer initMockServer() {
    return DatabaseTestService.initMockServer(
        IntegrationTestConfigurationUtil.createItConfigurationService()
            .getElasticSearchConfiguration()
            .getFirstConnectionNode());
  }

  @Override
  public DatabaseClient getDatabaseClient() {
    return prefixAwareRestHighLevelClient;
  }

  @Override
  public OptimizeElasticsearchClient getOptimizeElasticsearchClient() {
    return getOptimizeElasticClient();
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
    // If the MockServer has been used, we reset all expectations and logs and revert to the default
    // client
    if (prefixAwareRestHighLevelClient == CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY)) {
      log.info("Resetting all MockServer expectations and logs");
      mockServerClient.reset();
      log.info("No longer using ES MockServer");
      initEsClient();
    }
  }

  @Override
  public ClientAndServer useDBMockServer() {
    log.info("Using ElasticSearch MockServer");
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
      final RefreshRequest refreshAllIndicesRequest =
          new RefreshRequest(getIndexNameService().getIndexPrefix() + "*");
      getOptimizeElasticClient()
          .getHighLevelClient()
          .indices()
          .refresh(refreshAllIndicesRequest, getOptimizeElasticClient().requestOptions());
    } catch (final Exception e) {
      throw new OptimizeIntegrationTestException("Could not refresh Optimize indices!", e);
    }
  }

  @Override
  public void addEntryToDatabase(final String indexName, final String id, final Object entry) {
    try {
      final String json = OBJECT_MAPPER.writeValueAsString(entry);
      final IndexRequest request =
          new IndexRequest(indexName)
              .id(id)
              .source(json, XContentType.JSON)
              .setRefreshPolicy(
                  IMMEDIATE); // necessary in order to search for the entry immediately
      getOptimizeElasticClient().index(request);
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException("Unable to add an entry to elasticsearch", e);
    }
  }

  @Override
  public void addEntriesToDatabase(final String indexName, final Map<String, Object> idToEntryMap) {
    StreamSupport.stream(Iterables.partition(idToEntryMap.entrySet(), 10_000).spliterator(), false)
        .forEach(
            batch -> {
              final BulkRequest bulkRequest = new BulkRequest();
              for (final Map.Entry<String, Object> idAndObject : batch) {
                final String json = writeJsonString(idAndObject);
                final IndexRequest request =
                    new IndexRequest(indexName)
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
      final CountResponse countResponse =
          getOptimizeElasticClient().count(new CountRequest(indexName).query(matchAllQuery()));
      return Long.valueOf(countResponse.getCount()).intValue();
    } catch (final IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot evaluate document count for index " + indexName, e);
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
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(matchAllQuery())
            .fetchSource(false)
            .size(0)
            .aggregation(
                nested(FLOW_NODE_INSTANCES, FLOW_NODE_INSTANCES)
                    .subAggregation(
                        count(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION)
                            .field(
                                FLOW_NODE_INSTANCES
                                    + "."
                                    + ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID)));

    final SearchRequest searchRequest =
        new SearchRequest().indices(PROCESS_INSTANCE_MULTI_ALIAS).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest);
    } catch (final IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
          "Could not evaluate activity count in process instance indices.", e);
    }

    final Nested nested = searchResponse.getAggregations().get(FLOW_NODE_INSTANCES);
    final ValueCount countAggregator =
        nested.getAggregations().get(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION);
    return Long.valueOf(countAggregator.getValue()).intValue();
  }

  @Override
  public Integer getVariableInstanceCountForAllProcessInstances() {
    return getVariableInstanceCountForAllProcessInstances(QueryBuilders.matchAllQuery());
  }

  @Override
  public Integer getVariableInstanceCountForAllCompletedProcessInstances() {
    return getVariableInstanceCountForAllProcessInstances(
        boolQuery().must(existsQuery(ProcessInstanceIndex.END_DATE)));
  }

  @Override
  public void deleteAllOptimizeData() {
    final DeleteByQueryRequest request =
        new DeleteByQueryRequest(getIndexNameService().getIndexPrefix() + "*")
            .setQuery(matchAllQuery())
            .setRefresh(true);

    try {
      getOptimizeElasticClient()
          .getHighLevelClient()
          .deleteByQuery(request, getOptimizeElasticClient().requestOptions());
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all Optimize data", e);
    }
  }

  @SneakyThrows
  @Override
  public void deleteAllIndicesContainingTerm(final String indexTerm) {
    final String[] indicesToDelete =
        getOptimizeElasticClient().getAllIndexNames().stream()
            .filter(index -> index.contains(indexTerm))
            .toArray(String[]::new);
    if (indicesToDelete.length > 0) {
      getOptimizeElasticClient().deleteIndexByRawIndexNames(indicesToDelete);
    }
  }

  @Override
  public void deleteAllSingleProcessReports() {
    final DeleteByQueryRequest request =
        new DeleteByQueryRequest(
                getIndexNameService()
                    .getOptimizeIndexAliasForIndex(new SingleProcessReportIndexES()))
            .setQuery(matchAllQuery())
            .setRefresh(true);

    try {
      getOptimizeElasticClient()
          .getHighLevelClient()
          .deleteByQuery(request, getOptimizeElasticClient().requestOptions());
    } catch (final IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
          "Could not delete data in index "
              + getIndexNameService()
                  .getOptimizeIndexAliasForIndex(new SingleProcessReportIndexES()),
          e);
    }
  }

  @Override
  public void deleteExternalEventSequenceCountIndex() {
    deleteIndexOfMapping(new EventSequenceCountIndexES(EXTERNAL_EVENTS_INDEX_SUFFIX));
  }

  @Override
  public void deleteTerminatedSessionsIndex() {
    deleteIndexOfMapping(new TerminatedUserSessionIndexES());
  }

  @Override
  public void deleteAllVariableUpdateInstanceIndices() {
    final String[] indexNames =
        getOptimizeElasticClient()
            .getAllIndicesForAlias(
                getIndexNameService()
                    .getOptimizeIndexAliasForIndex(new VariableUpdateInstanceIndexES()))
            .toArray(String[]::new);
    getOptimizeElasticClient().deleteIndexByRawIndexNames(indexNames);
  }

  @Override
  public void deleteAllExternalVariableIndices() {
    final String[] indexNames =
        getOptimizeElasticClient()
            .getAllIndicesForAlias(
                getIndexNameService()
                    .getOptimizeIndexAliasForIndex(new ExternalProcessVariableIndexES()))
            .toArray(String[]::new);
    getOptimizeElasticClient().deleteIndexByRawIndexNames(indexNames);
  }

  @Override
  public boolean indexExists(final String indexOrAliasName) {
    final GetIndexRequest request = new GetIndexRequest(indexOrAliasName);
    try {
      return getOptimizeElasticClient().exists(request);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not check if [%s] index already exist.", String.join(",", indexOrAliasName));
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  @SneakyThrows
  public boolean zeebeIndexExists(final String indexName) {
    final OptimizeElasticsearchClient esClient = getOptimizeElasticClient();
    return esClient
        .getHighLevelClient()
        .indices()
        .exists(new GetIndexRequest(indexName), esClient.requestOptions());
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return prefixAwareRestHighLevelClient;
  }

  @SneakyThrows
  @Override
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(
      final String dbType, final String engine) {
    final GetRequest getRequest =
        new GetRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
            .id(DatabaseHelper.constructKey(dbType, engine));
    final GetResponse response = prefixAwareRestHighLevelClient.get(getRequest);
    if (response.isExists()) {
      return OBJECT_MAPPER
          .readValue(response.getSourceAsString(), TimestampBasedImportIndexDto.class)
          .getTimestampOfLastEntity();
    } else {
      throw new NotFoundException(
          String.format(
              "Timestamp based import index does not exist: esType: {%s}, engine: {%s}",
              dbType, engine));
    }
  }

  @Override
  public void deleteAllExternalEventIndices() {
    final String eventIndexAlias =
        getIndexNameService().getOptimizeIndexAliasForIndex(new EventIndexES());
    final String[] eventIndices =
        getOptimizeElasticClient().getAllIndicesForAlias(eventIndexAlias).toArray(String[]::new);
    getOptimizeElasticClient().deleteIndexByRawIndexNames(eventIndices);
  }

  @SneakyThrows
  @Override
  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    final String[] indicesToDelete =
        Arrays.stream(
                getOptimizeElasticClient()
                    .getHighLevelClient()
                    .indices()
                    .get(
                        new GetIndexRequest("*").indicesOptions(INDICES_EXIST_OPTIONS),
                        getOptimizeElasticClient().requestOptions())
                    .getIndices())
            .filter(indexName -> indexName.contains(zeebeRecordPrefix))
            .toArray(String[]::new);
    if (indicesToDelete.length > 1) {
      getOptimizeElasticClient().deleteIndexByRawIndexNames(indicesToDelete);
    }
  }

  @SneakyThrows
  @Override
  public void deleteAllOtherZeebeRecordsWithPrefix(
      final String zeebeRecordPrefix, final String recordsToKeep) {
    final String[] indicesToDelete =
        Arrays.stream(
                getOptimizeElasticClient()
                    .getHighLevelClient()
                    .indices()
                    .get(
                        new GetIndexRequest("*").indicesOptions(INDICES_EXIST_OPTIONS),
                        getOptimizeElasticClient().requestOptions())
                    .getIndices())
            .filter(
                indexName ->
                    indexName.contains(zeebeRecordPrefix) && !indexName.contains(recordsToKeep))
            .toArray(String[]::new);
    if (indicesToDelete.length > 1) {
      getOptimizeElasticClient().deleteIndexByRawIndexNames(indicesToDelete);
    }
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsForPrefix(
      final String zeebeRecordPrefix, final String indexName, final String updateScript) {
    final UpdateByQueryRequest update =
        new UpdateByQueryRequest(zeebeRecordPrefix + "_" + indexName + "*")
            .setQuery(matchAllQuery())
            .setScript(
                new Script(
                    ScriptType.INLINE,
                    Script.DEFAULT_SCRIPT_LANG,
                    updateScript,
                    Collections.emptyMap()))
            .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
            .setRefresh(true);
    getOptimizeElasticClient()
        .getHighLevelClient()
        .updateByQuery(update, getOptimizeElasticClient().requestOptions());
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsWithPositionForPrefix(
      final String zeebeRecordPrefix,
      final String indexName,
      final long position,
      final String updateScript) {
    final UpdateByQueryRequest update =
        new UpdateByQueryRequest(zeebeRecordPrefix + "_" + indexName + "*")
            .setQuery(boolQuery().must(termQuery(ZeebeRecordDto.Fields.position, position)))
            .setScript(
                new Script(
                    ScriptType.INLINE,
                    Script.DEFAULT_SCRIPT_LANG,
                    updateScript,
                    Collections.emptyMap()))
            .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
            .setRefresh(true);
    getOptimizeElasticClient()
        .getHighLevelClient()
        .updateByQuery(update, getOptimizeElasticClient().requestOptions());
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsOfBpmnElementTypeForPrefix(
      final String zeebeRecordPrefix,
      final BpmnElementType bpmnElementType,
      final String updateScript) {
    final UpdateByQueryRequest update =
        new UpdateByQueryRequest(zeebeRecordPrefix + "_" + ZEEBE_PROCESS_INSTANCE_INDEX_NAME + "*")
            .setQuery(
                boolQuery()
                    .must(
                        termQuery(
                            ZeebeRecordDto.Fields.value
                                + "."
                                + ZeebeProcessInstanceDataDto.Fields.bpmnElementType,
                            bpmnElementType.name())))
            .setScript(
                new Script(
                    ScriptType.INLINE,
                    Script.DEFAULT_SCRIPT_LANG,
                    updateScript,
                    Collections.emptyMap()))
            .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
            .setRefresh(true);
    getOptimizeElasticClient()
        .getHighLevelClient()
        .updateByQuery(update, getOptimizeElasticClient().requestOptions());
  }

  @SneakyThrows
  @Override
  public void updateUserTaskDurations(
      final String processInstanceId, final String processDefinitionKey, final long duration) {
    final String updateScript = buildUpdateScript(duration);
    final UpdateRequest update =
        new UpdateRequest()
            .index(getProcessInstanceIndexAliasName(processDefinitionKey))
            .id(processInstanceId)
            .script(
                new Script(
                    ScriptType.INLINE,
                    Script.DEFAULT_SCRIPT_LANG,
                    updateScript,
                    Collections.emptyMap()))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    getOptimizeElasticClient().update(update);
  }

  @Override
  public Map<AggregationDto, Double> calculateExpectedValueGivenDurations(
      final Number... setDuration) {
    // For percentiles, the result we get from ES depends on the version being used
    if (isDatabaseVersionGreaterThanOrEqualTo("8.9.0")) {
      // Versions newer than 8.9.0 used interpolation to determine percentiles, so we use a library
      // that does the same to
      // calculate the expected results
      return calculateExpectedValueGivenDurationsWithPercentileInterpolation(setDuration);
    } else {
      // Versions before 8.9.0 used the TDigest algorithm internally without interpolation, so we
      // must use the same
      // here when calculating the expected results
      return calculateExpectedValueGivenDurationsWithoutPercentileInterpolation(setDuration);
    }
  }

  @Override
  public long countRecordsByQuery(
      final TermsQueryContainer termsQueryContainer, final String expectedIndex) {
    final BoolQueryBuilder boolQueryBuilder = termsQueryContainer.toElasticSearchQuery();
    return countRecordsByQuery(boolQueryBuilder, expectedIndex);
  }

  private long countRecordsByQuery(
      final BoolQueryBuilder boolQueryBuilder, final String expectedIndex) {
    final OptimizeElasticsearchClient esClient = getOptimizeElasticClient();
    final CountRequest countRequest = new CountRequest(expectedIndex).query(boolQueryBuilder);
    try {
      return esClient
          .getHighLevelClient()
          .count(countRequest, esClient.requestOptions())
          .getCount();
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  @SneakyThrows
  public <T> List<T> getZeebeExportedRecordsByQuery(
      final String exportIndex, final TermsQueryContainer query, final Class<T> zeebeRecordClass) {
    final OptimizeElasticsearchClient esClient = getOptimizeElasticClient();
    final BoolQueryBuilder boolQueryBuilder = query.toElasticSearchQuery();
    final SearchRequest searchRequest =
        new SearchRequest()
            .indices(exportIndex)
            .source(
                new SearchSourceBuilder().query(boolQueryBuilder).trackTotalHits(true).size(100));
    final SearchResponse searchResponse = esClient.searchWithoutPrefixing(searchRequest);
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), zeebeRecordClass, OPTIMIZE_MAPPER);
  }

  @Override
  public void updateEventProcessRoles(
      final String eventProcessId, final List<IdentityDto> identityDtos) {
    try {
      final UpdateRequest request =
          new UpdateRequest(EVENT_PROCESS_MAPPING_INDEX_NAME, eventProcessId)
              .script(
                  new Script(
                      ScriptType.INLINE,
                      Script.DEFAULT_SCRIPT_LANG,
                      "ctx._source.roles = params.updatedRoles;",
                      Collections.singletonMap(
                          "updatedRoles",
                          getObjectMapper()
                              .convertValue(
                                  mapIdentityDtosToEventProcessRoleRequestDto(identityDtos),
                                  Object.class))))
              .setRefreshPolicy(IMMEDIATE);
      final UpdateResponse updateResponse = getOptimizeElasticClient().update(request);
      if (updateResponse.getShardInfo().getFailed() > 0) {
        throw new OptimizeIntegrationTestException(
            String.format(
                "Was not able to update event process roles with id [%s].", eventProcessId));
      }
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException("Unable to update event process roles.", e);
    }
  }

  @Override
  @SneakyThrows
  public Map<String, List<AliasMetadata>> getEventProcessInstanceIndicesWithAliasesFromDatabase() {
    final OptimizeElasticsearchClient esClient = getOptimizeElasticClient();
    final OptimizeIndexNameService indexNameService = esClient.getIndexNameService();
    final GetIndexResponse getIndexResponse =
        esClient
            .getHighLevelClient()
            .indices()
            .get(
                new GetIndexRequest(
                    indexNameService.getOptimizeIndexAliasForIndex(
                            EVENT_PROCESS_INSTANCE_INDEX_PREFIX)
                        + "*"),
                esClient.requestOptions());
    return getIndexResponse.getAliases();
  }

  @Override
  @SneakyThrows
  public Optional<EventProcessPublishStateDto> getEventProcessPublishStateDtoFromDatabase(
      final String processMappingId) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                boolQuery()
                    .must(
                        termQuery(
                            EventProcessPublishStateIndexES.PROCESS_MAPPING_ID, processMappingId))
                    .must(termQuery(EventProcessPublishStateIndexES.DELETED, false)))
            .sort(
                SortBuilders.fieldSort(EventProcessPublishStateIndexES.PUBLISH_DATE_TIME)
                    .order(SortOrder.DESC))
            .size(1);
    final SearchResponse searchResponse =
        getOptimizeElasticClient()
            .search(
                new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
                    .source(searchSourceBuilder));

    EventProcessPublishStateDto result = null;
    if (searchResponse.getHits().getTotalHits().value > 0) {
      result =
          getObjectMapper()
              .readValue(
                  searchResponse.getHits().getAt(0).getSourceAsString(),
                  EsEventProcessPublishStateDto.class)
              .toEventProcessPublishStateDto();
    }
    return Optional.ofNullable(result);
  }

  @Override
  @SneakyThrows
  public Optional<EventProcessDefinitionDto> getEventProcessDefinitionFromDatabase(
      final String definitionId) {
    final GetResponse getResponse =
        getOptimizeElasticClient()
            .get(new GetRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME).id(definitionId));

    EventProcessDefinitionDto result = null;
    if (getResponse.isExists()) {
      result =
          getObjectMapper()
              .readValue(getResponse.getSourceAsString(), EventProcessDefinitionDto.class);
    }

    return Optional.ofNullable(result);
  }

  @Override
  @SneakyThrows
  public List<EventProcessInstanceDto> getEventProcessInstancesFromDatabaseForProcessPublishStateId(
      final String publishStateId) {
    final List<EventProcessInstanceDto> results = new ArrayList<>();
    final SearchResponse searchResponse =
        getOptimizeElasticClient()
            .search(
                new SearchRequest(EventProcessInstanceIndex.constructIndexName(publishStateId)));
    for (final SearchHit hit : searchResponse.getHits().getHits()) {
      results.add(
          getObjectMapper().readValue(hit.getSourceAsString(), EventProcessInstanceDto.class));
    }
    return results;
  }

  @Override
  @SneakyThrows
  protected <T extends OptimizeDto> List<T> getInstancesById(
      final String indexName,
      final List<String> instanceIds,
      final String idField,
      final Class<T> type) {
    final List<T> results = new ArrayList<>();
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(termsQuery(idField, instanceIds))
            .trackTotalHits(true)
            .size(100);

    final SearchRequest searchRequest =
        new SearchRequest().indices(indexName).source(searchSourceBuilder);

    final SearchResponse searchResponse = getOptimizeElasticClient().search(searchRequest);
    for (final SearchHit hit : searchResponse.getHits().getHits()) {
      results.add(getObjectMapper().readValue(hit.getSourceAsString(), type));
    }
    return results;
  }

  @Override
  @SneakyThrows
  public <T> Optional<T> getDatabaseEntryById(
      final String indexName, final String entryId, final Class<T> type) {
    final GetRequest getRequest = new GetRequest().index(indexName).id(entryId);
    final GetResponse getResponse = getOptimizeElasticClient().get(getRequest);
    if (getResponse.isExists()) {
      return Optional.of(getObjectMapper().readValue(getResponse.getSourceAsString(), type));
    } else {
      return Optional.empty();
    }
  }

  @Override
  @SneakyThrows
  public void deleteProcessInstancesFromIndex(final String indexName, final String id) {
    final DeleteRequest request = new DeleteRequest(indexName).id(id).setRefreshPolicy(IMMEDIATE);
    getOptimizeElasticClient().delete(request);
  }

  @Override
  @SneakyThrows
  public void deleteDatabaseEntryById(final String indexName, final String id) {
    final DeleteRequest request = new DeleteRequest(indexName, id).setRefreshPolicy(IMMEDIATE);
    getOptimizeElasticClient().delete(request);
  }

  @Override
  public DatabaseType getDatabaseVendor() {
    return DatabaseType.ELASTICSEARCH;
  }

  @SneakyThrows
  private boolean isDatabaseVersionGreaterThanOrEqualTo(final String dbVersion) {
    return Stream.of(dbVersion, getDatabaseVersion())
        .map(ModuleDescriptor.Version::parse)
        .sorted()
        .findFirst()
        .map(firstVersion -> firstVersion.toString().equals(dbVersion))
        .orElseThrow(() -> new OptimizeIntegrationTestException("Could not determine ES version"));
  }

  @Override
  @SneakyThrows
  public String getDatabaseVersion() {
    if (elasticsearchDatabaseVersion == null) {
      elasticsearchDatabaseVersion =
          ElasticsearchHighLevelRestClientBuilder.getCurrentESVersion(
              getOptimizeElasticClient().getHighLevelClient(),
              getOptimizeElasticClient().requestOptions());
    }
    return elasticsearchDatabaseVersion;
  }

  @Override
  public int getNestedDocumentsLimit(final ConfigurationService configurationService) {
    return configurationService.getElasticSearchConfiguration().getNestedDocumentsLimit();
  }

  @Override
  public void setNestedDocumentsLimit(
      final ConfigurationService configurationService, final int nestedDocumentsLimit) {
    configurationService
        .getElasticSearchConfiguration()
        .setNestedDocumentsLimit(nestedDocumentsLimit);
  }

  @Override
  @SneakyThrows
  public void updateProcessInstanceNestedDocLimit(
      final String processDefinitionKey,
      final int nestedDocLimit,
      final ConfigurationService configurationService) {
    setNestedDocumentsLimit(configurationService, nestedDocLimit);
    final OptimizeElasticsearchClient esClient = getOptimizeElasticsearchClient();
    final String indexName =
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionForAllIndicesOf(
                new ProcessInstanceIndexES(processDefinitionKey));

    esClient
        .getHighLevelClient()
        .indices()
        .putSettings(
            new UpdateSettingsRequest(buildDynamicSettings(configurationService), indexName),
            esClient.requestOptions());
  }

  @Override
  public void createIndex(
      final String optimizeIndexNameWithVersion, final String optimizeIndexAliasForIndex)
      throws IOException {
    final CreateIndexRequest request = new CreateIndexRequest(optimizeIndexNameWithVersion);
    request.alias(new Alias(optimizeIndexAliasForIndex).writeIndex(true));
    getOptimizeElasticsearchClient()
        .getHighLevelClient()
        .indices()
        .create(request, getOptimizeElasticsearchClient().requestOptions());
  }

  @SneakyThrows
  @Override
  public void deleteIndicesStartingWithPrefix(final String term) {
    final String[] indicesToDelete =
        getOptimizeElasticClient().getAllIndexNames().stream()
            .filter(
                indexName ->
                    indexName.startsWith(getIndexNameService().getIndexPrefix() + "-" + term))
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

  private void createClientAndAddToCache(
      final String clientKey, final ConfigurationService configurationService) {
    final DatabaseConnectionNodeConfiguration esConfig =
        configurationService.getElasticSearchConfiguration().getFirstConnectionNode();
    log.info(
        "Creating ES Client with host {} and port {}", esConfig.getHost(), esConfig.getHttpPort());
    prefixAwareRestHighLevelClient =
        new OptimizeElasticsearchClient(
            ElasticsearchHighLevelRestClientBuilder.build(configurationService),
            new OptimizeIndexNameService(configurationService, DatabaseType.ELASTICSEARCH),
            OPTIMIZE_MAPPER);
    adjustClusterSettings();
    CLIENT_CACHE.put(clientKey, prefixAwareRestHighLevelClient);
  }

  private ConfigurationService createConfigurationService() {
    final ConfigurationService configurationService =
        IntegrationTestConfigurationUtil.createItConfigurationService();
    if (customIndexPrefix != null) {
      configurationService
          .getElasticSearchConfiguration()
          .setIndexPrefix(
              configurationService.getElasticSearchConfiguration().getIndexPrefix()
                  + customIndexPrefix);
    }
    return configurationService;
  }

  private void adjustClusterSettings() {
    final Settings settings =
        Settings.builder()
            // we allow auto index creation because the Zeebe exporter creates indices for records
            .put("action.auto_create_index", true)
            // all of our tests are running against a one node cluster. Since we're creating a lot
            // of indexes,
            // we are easily hitting the default value of 1000. Thus, we need to increase this value
            // for the test setup.
            .put("cluster.max_shards_per_node", 10_000)
            .build();
    final ClusterUpdateSettingsRequest clusterUpdateSettingsRequest =
        new ClusterUpdateSettingsRequest();
    clusterUpdateSettingsRequest.persistentSettings(settings);
    try (final XContentBuilder builder = jsonBuilder()) {
      // low level request as we need body serialized with flat_settings option for AWS hosted
      // elasticsearch support
      final Request request = new Request("PUT", "/_cluster/settings");
      request.setJsonEntity(
          Strings.toString(
              clusterUpdateSettingsRequest.toXContent(builder, XCONTENT_PARAMS_FLAT_SETTINGS)));
      prefixAwareRestHighLevelClient.getLowLevelClient().performRequest(request);
    } catch (final IOException e) {
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

  private <T> List<T> getAllDocumentsOfIndexAs(
      final String indexName, final Class<T> type, final QueryBuilder query) {
    try {
      return getAllDocumentsOfIndicesAs(new String[] {indexName}, type, query);
    } catch (final ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot get all documents for index " + indexName, e);
    }
  }

  @SneakyThrows
  private <T> List<T> getAllDocumentsOfIndicesAs(
      final String[] indexNames, final Class<T> type, final QueryBuilder query) {

    final List<T> results = new ArrayList<>();
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).trackTotalHits(true).size(100);

    // Requests to optimize indexes require prefixing, while zeebe indexes don't. So differentiating
    // both here to perform the search request properly
    final Map<String, List<String>> groupedByPrefix =
        Arrays.stream(indexNames)
            .collect(
                Collectors.groupingBy(
                    name ->
                        name.startsWith(ZEEBE_RECORD_TEST_PREFIX)
                            ? "ZeebeIndex"
                            : "OptimizeIndex"));

    if (groupedByPrefix.containsKey("ZeebeIndex")) {
      final SearchRequest searchRequest =
          new SearchRequest().indices(indexNames).source(searchSourceBuilder);
      final SearchResponse response =
          getOptimizeElasticClient().searchWithoutPrefixing(searchRequest);
      results.addAll(mapHits(response.getHits(), type, getObjectMapper()));
    }

    if (groupedByPrefix.containsKey("OptimizeIndex")) {
      final SearchRequest searchRequest =
          new SearchRequest().indices(indexNames).source(searchSourceBuilder);
      final SearchResponse response = getOptimizeElasticClient().search(searchRequest);
      results.addAll(mapHits(response.getHits(), type, getObjectMapper()));
    }

    return results;
  }

  private int getInstanceCountWithQuery(final BoolQueryBuilder query) {
    try {
      final CountResponse countResponse =
          getOptimizeElasticClient()
              .count(new CountRequest(PROCESS_INSTANCE_MULTI_ALIAS).query(query));
      return Long.valueOf(countResponse.getCount()).intValue();
    } catch (final IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot evaluate document count for index " + PROCESS_INSTANCE_MULTI_ALIAS, e);
    }
  }

  private Integer getVariableInstanceCountForAllProcessInstances(
      final QueryBuilder processInstanceQuery) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(processInstanceQuery).fetchSource(false).size(0);

    final SearchRequest searchRequest =
        new SearchRequest().indices(PROCESS_INSTANCE_MULTI_ALIAS).source(searchSourceBuilder);

    searchSourceBuilder.aggregation(
        nested(VARIABLES, VARIABLES)
            .subAggregation(count("count").field(getNestedVariableIdField())));

    final SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest);
    } catch (final IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot evaluate variable instance count in process instance indices.", e);
    }

    final Nested nestedAgg = searchResponse.getAggregations().get(VARIABLES);
    final ValueCount countAggregator = nestedAgg.getAggregations().get("count");
    final long totalVariableCount = countAggregator.getValue();

    return Long.valueOf(totalVariableCount).intValue();
  }

  private void deleteIndexOfMapping(final IndexMappingCreator<XContentBuilder> indexMapping) {
    getOptimizeElasticClient().deleteIndex(indexMapping);
  }
}
