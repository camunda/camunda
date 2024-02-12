/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension.db;

import com.google.common.collect.Iterables;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import jakarta.ws.rs.NotSupportedException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClientFactory;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import org.camunda.optimize.service.db.os.schema.index.ExternalProcessVariableIndexOS;
import org.camunda.optimize.service.db.os.schema.index.TerminatedUserSessionIndexOS;
import org.camunda.optimize.service.db.os.schema.index.VariableUpdateInstanceIndexOS;
import org.camunda.optimize.service.db.os.schema.index.events.EventIndexOS;
import org.camunda.optimize.service.db.os.schema.index.events.EventSequenceCountIndexOS;
import org.camunda.optimize.service.db.os.schema.index.report.SingleProcessReportIndexOS;
import org.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.DatabaseType;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.jetbrains.annotations.NotNull;
import org.mockserver.integration.ClientAndServer;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.cluster.PutClusterSettingsRequest;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TrackHits;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.testcontainers.shaded.org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsWithPercentileInterpolation;

@Slf4j
public class OpenSearchDatabaseTestService extends DatabaseTestService {

  private static final String MOCKSERVER_CLIENT_KEY = "MockServer";
  private static final Map<String, OptimizeOpenSearchClient> CLIENT_CACHE = new HashMap<>();
  private static final ClientAndServer mockServerClient = initMockServer();

  private String opensearchDatabaseVersion;

  private OptimizeOpenSearchClient prefixAwareOptimizeOpenSearchClient;

  public OpenSearchDatabaseTestService(final String customIndexPrefix,
                                       final boolean haveToClean) {
    super(customIndexPrefix, haveToClean);
    initOsClient();
  }

  @Override
  public DatabaseClient getDatabaseClient() {
    return prefixAwareOptimizeOpenSearchClient;
  }

  @Override
  public OptimizeElasticsearchClient getOptimizeElasticsearchClient() {
    // TODO get rid of this method with OPT-7455
    throw new NotSupportedException("No ElasticSearch client here");
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
    if (prefixAwareOptimizeOpenSearchClient == CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY)) {
      log.info("Resetting all MockServer expectations and logs");
      mockServerClient.reset();
      log.info("No longer using OS MockServer");
      initOsClient();
    }
  }

  @Override
  public ClientAndServer useDBMockServer() {
    log.debug("Using OpenSearch MockServer");
    if (CLIENT_CACHE.containsKey(MOCKSERVER_CLIENT_KEY)) {
      prefixAwareOptimizeOpenSearchClient = CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY);
    } else {
      final ConfigurationService configurationService = createConfigurationService();
      final DatabaseConnectionNodeConfiguration osConfig =
        configurationService.getOpenSearchConfiguration().getFirstConnectionNode();
      osConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
      osConfig.setHttpPort(mockServerClient.getLocalPort());
      createClientAndAddToCache(MOCKSERVER_CLIENT_KEY, configurationService);
    }
    return mockServerClient;
  }

  @Override
  public void refreshAllOptimizeIndices() {
    try {
      RefreshRequest refreshAllIndicesRequest =
        new RefreshRequest.Builder()
          .index(getIndexNameService().getIndexPrefix() + "*")
          .build();

      getOptimizeOpenSearchClient().getOpenSearchClient()
        .indices()
        .refresh(refreshAllIndicesRequest);
    } catch (Exception e) {
      throw new OptimizeIntegrationTestException("Could not refresh Optimize indices!", e);
    }
  }

  @Override
  public void addEntryToDatabase(String indexName, String id, Object entry) {
    final IndexRequest.Builder request = createIndexRequestBuilder(indexName, id, entry);
    final IndexResponse response = getOptimizeOpenSearchClient().getRichOpenSearchClient()
      .doc()
      .index(request);
    if (!response.shards().failures().isEmpty()) {
      final String reason = String.format("Could not add entry to index %s with id %s and entry %s", indexName, id, entry);
      throw new OptimizeIntegrationTestException(reason);
    }
  }

  @Override
  public void addEntriesToDatabase(String indexName, Map<String, Object> idToEntryMap) {
    StreamSupport.stream(Iterables.partition(idToEntryMap.entrySet(), 10_000).spliterator(), false)
      .forEach(batch -> {
        final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
        List<BulkOperation> operations = new ArrayList<>();
        for (Map.Entry<String, Object> idAndObject : batch) {
          IndexOperation.Builder<Object> operation =
            new IndexOperation.Builder<>().document(idAndObject.getValue()).id(idAndObject.getKey());
          operations.add(operation.build()._toBulkOperation());
        }
        bulkRequestBuilder.operations(operations)
          .index(indexName);
        prefixAwareOptimizeOpenSearchClient.doBulkRequest(
          bulkRequestBuilder,
          operations,
          "add entries",
          false
        );
      });

  }

  @Override
  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type) {
    return getAllDocumentsOfIndexAs(indexName, type, QueryDSL.matchAll());
  }

  @Override
  public Integer getDocumentCountOf(final String indexName) {
    try {
      return Long.valueOf(getOptimizeOpenSearchClient()
                            .count(new String[]{indexName}, QueryDSL.matchAll())).intValue();
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate document count for index " + indexName,
        e
      );
    }
  }

  @Override
  public Integer getCountOfCompletedInstances() {
    return getInstanceCountWithQuery(QueryDSL.exists(ProcessInstanceIndex.END_DATE));
  }

  @Override
  public Integer getCountOfCompletedInstancesWithIdsIn(final Set<Object> processInstanceIds) {
    Set<String> stringProcessInstanceIds = processInstanceIds.stream()
      .map(Object::toString)
      .collect(Collectors.toSet());

    return getInstanceCountWithQuery(
      QueryDSL.and(
        QueryDSL.exists(ProcessInstanceIndex.END_DATE),
        QueryDSL.stringTerms(ProcessInstanceIndex.PROCESS_INSTANCE_ID, stringProcessInstanceIds)
      )
    );
  }

  @Override
  public Integer getActivityCountForAllProcessInstances() {
    // TODO implement with #11121
    throw new NotImplementedException("Not yet implemented for OpenSearch, will be implemented with issue #11121");
//    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
//      .query(QueryDSL.matchAll())
//      .fetchSource(false)
//      .size(0)
//      .aggregation(
//        nested(FLOW_NODE_INSTANCES, FLOW_NODE_INSTANCES)
//          .subAggregation(
//            count(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION)
//              .field(FLOW_NODE_INSTANCES + "." + ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID)
//          )
//      );
//
//    SearchRequest searchRequest = new SearchRequest()
//      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
//      .source(searchSourceBuilder);
//
//    SearchResponse searchResponse;
//    try {
//      searchResponse = getOptimizeOpenClient().search(searchRequest);
//    } catch (IOException | OpensearchStatusException e) {
//      throw new OptimizeIntegrationTestException("Could not evaluate activity count in process instance indices.", e);
//    }
//
//    Nested nested = searchResponse.getAggregations()
//      .get(FLOW_NODE_INSTANCES);
//    ValueCount countAggregator =
//      nested.getAggregations()
//        .get(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION);
//    return Long.valueOf(countAggregator.getValue()).intValue();
  }

  @Override
  public Integer getVariableInstanceCountForAllProcessInstances() {
    return getVariableInstanceCountForAllProcessInstances(QueryDSL.matchAll());
  }

  @Override
  public Integer getVariableInstanceCountForAllCompletedProcessInstances() {
    return getVariableInstanceCountForAllProcessInstances(QueryDSL.exists(ProcessInstanceIndex.END_DATE));
  }

  @Override
  public void deleteAllOptimizeData() {
    getOptimizeOpenSearchClient()
      .getRichOpenSearchClient().doc()
      .deleteByQuery(QueryDSL.matchAll(), true, getIndexNameService().getIndexPrefix() + "*");
  }

  @SneakyThrows
  @Override
  public void deleteAllIndicesContainingTerm(final String indexTerm) {
    getOptimizeOpenSearchClient().getRichOpenSearchClient().index().deleteIndicesWithRetries(indexTerm + "*");
  }

  @Override
  public void deleteAllSingleProcessReports() {
    getOptimizeOpenSearchClient()
      .getRichOpenSearchClient().doc()
      .deleteByQuery(
        QueryDSL.matchAll(), true,
        getIndexNameService().getOptimizeIndexAliasForIndex(new SingleProcessReportIndexOS())
      );
  }

  @Override
  public void deleteExternalEventSequenceCountIndex() {
    deleteIndexOfMapping(new EventSequenceCountIndexOS(EXTERNAL_EVENTS_INDEX_SUFFIX));
  }

  @Override
  public void deleteTerminatedSessionsIndex() {
    deleteIndexOfMapping(new TerminatedUserSessionIndexOS());
  }

  @Override
  public void deleteAllVariableUpdateInstanceIndices() {
    final String[] indexNames = getOptimizeOpenSearchClient().getAllIndicesForAlias(
      getIndexNameService().getOptimizeIndexAliasForIndex(new VariableUpdateInstanceIndexOS())).toArray(String[]::new);
    deleteIndices(indexNames);
  }

  @Override
  public void deleteAllExternalVariableIndices() {
    final String[] indexNames = getOptimizeOpenSearchClient().getAllIndicesForAlias(
      getIndexNameService().getOptimizeIndexAliasForIndex(new ExternalProcessVariableIndexOS())).toArray(String[]::new);
    deleteIndices(indexNames);
  }

  @Override
  public boolean indexExists(final String indexOrAliasName) {
    return getOptimizeOpenSearchClient()
      .getRichOpenSearchClient().index()
      .indexExists(indexOrAliasName);
  }

  @SneakyThrows
  @Override
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(final String dbType, final String engine) {
    Optional<TimestampBasedImportIndexDto> response = prefixAwareOptimizeOpenSearchClient.getRichOpenSearchClient()
      .doc()
      .getWithRetries(
        TIMESTAMP_BASED_IMPORT_INDEX_NAME,
        DatabaseHelper.constructKey(dbType, engine),
        TimestampBasedImportIndexDto.class
      );

    return response.map(TimestampBasedImportIndexDto::getTimestampOfLastEntity)
      .orElseThrow(() -> new OptimizeIntegrationTestException(String.format(
        "Timestamp based import index does not exist: dbType: {%s}, engine: {%s}",
        dbType,
        engine
      )));
  }

  @Override
  public void deleteAllExternalEventIndices() {
    final String eventIndexAlias = getIndexNameService().getOptimizeIndexAliasForIndex(new EventIndexOS());
    final String[] eventIndices = getOptimizeOpenSearchClient().getAllIndicesForAlias(eventIndexAlias).toArray(String[]::new);
    deleteIndices(eventIndices);
  }

  @SneakyThrows
  @Override
  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    final GetIndexResponse allIndices = getOptimizeOpenSearchClient().getRichOpenSearchClient()
      .index()
      .get(RequestDSL.getIndexRequestBuilder("*").ignoreUnavailable(true));

    final String[] indicesToDelete = allIndices.result().keySet().stream()
      .filter(indexName -> indexName.contains(zeebeRecordPrefix))
      .toArray(String[]::new);

    if (indicesToDelete.length > 1) {
      deleteIndices(indicesToDelete);
    }
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsForPrefix(final String zeebeRecordPrefix, final String indexName,
                                          final String updateScript) {
    updateZeebeRecordsByQuery(zeebeRecordPrefix, indexName, QueryDSL.matchAll(), updateScript);
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsWithPositionForPrefix(final String zeebeRecordPrefix, final String indexName,
                                                      final long position, final String updateScript) {
    updateZeebeRecordsByQuery(
      zeebeRecordPrefix,
      indexName,
      QueryDSL.term(ZeebeRecordDto.Fields.position, position),
      updateScript
    );
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsOfBpmnElementTypeForPrefix(final String zeebeRecordPrefix,
                                                           final BpmnElementType bpmnElementType,
                                                           final String updateScript) {
    updateZeebeRecordsByQuery(
      zeebeRecordPrefix,
      ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      QueryDSL.term(
        ZeebeRecordDto.Fields.value + "." + ZeebeProcessInstanceDataDto.Fields.bpmnElementType,
        bpmnElementType.name()
      ),
      updateScript
    );
  }

  @SneakyThrows
  @Override
  public void updateUserTaskDurations(final String processInstanceId,
                                      final String processDefinitionKey,
                                      final long duration) {
    final String updateScript = buildUpdateScript(duration);
    updateRecordsByQuery(
      getProcessInstanceIndexAliasName(processDefinitionKey),
      QueryDSL.ids(processInstanceId),
      updateScript
    );
  }

  @Override
  public Map<AggregationDto, Double> calculateExpectedValueGivenDurations(final Number... setDuration) {
    return calculateExpectedValueGivenDurationsWithPercentileInterpolation(setDuration);
  }

  @SneakyThrows
  @Override
  public void deleteIndicesStartingWithPrefix(final String term) {
    final String[] indicesToDelete =
      getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .index()
        .getIndexNamesWithRetries(getIndexNameService().getIndexPrefix() + "-" + term + "*")
        .toArray(String[]::new);
    if (indicesToDelete.length > 0) {
      deleteIndices(indicesToDelete);
    }
  }

  private OptimizeOpenSearchClient getOptimizeOpenSearchClient() {
    return prefixAwareOptimizeOpenSearchClient;
  }

  private void initOsClient() {
    if (CLIENT_CACHE.containsKey(customIndexPrefix)) {
      prefixAwareOptimizeOpenSearchClient = CLIENT_CACHE.get(customIndexPrefix);
    } else {
      createClientAndAddToCache(customIndexPrefix, createConfigurationService());
    }
  }

  private static ClientAndServer initMockServer() {
    return DatabaseTestService.initMockServer(
      IntegrationTestConfigurationUtil
        .createItConfigurationService()
        .getOpenSearchConfiguration()
        .getFirstConnectionNode());
  }

  private void createClientAndAddToCache(String clientKey, ConfigurationService configurationService) {
    final DatabaseConnectionNodeConfiguration osConfig =
      configurationService.getOpenSearchConfiguration().getFirstConnectionNode();
    log.info("Creating OS Client with host {} and port {}", osConfig.getHost(), osConfig.getHttpPort());
    prefixAwareOptimizeOpenSearchClient = new OptimizeOpenSearchClient(
      OptimizeOpenSearchClientFactory.buildOpenSearchClientFromConfig(configurationService),
      OptimizeOpenSearchClientFactory.buildOpenSearchAsyncClientFromConfig(configurationService),
      new OptimizeIndexNameService(configurationService, DatabaseType.OPENSEARCH)
    );
    adjustClusterSettings();
    CLIENT_CACHE.put(clientKey, prefixAwareOptimizeOpenSearchClient);
  }

  private ConfigurationService createConfigurationService() {
    final ConfigurationService configurationService = IntegrationTestConfigurationUtil.createItConfigurationService();
    if (customIndexPrefix != null) {
      configurationService.getOpenSearchConfiguration()
        .setIndexPrefix(configurationService.getOpenSearchConfiguration().getIndexPrefix() + customIndexPrefix);
    }
    return configurationService;
  }

  private void adjustClusterSettings() {
    PutClusterSettingsRequest.Builder settings = new PutClusterSettingsRequest.Builder()
      .persistent("action.auto_create_index", JsonData.of(true))
      .persistent("cluster.max_shards_per_node", JsonData.of(10000))
      .flatSettings(true);
    try {
      getOptimizeOpenSearchClient().getOpenSearchClient().cluster().putSettings(settings.build());
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not update cluster settings!", e);
    }
  }

  private <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type, final Query query) {
    return getAllDocumentsOfIndicesAs(new String[]{indexName}, type, query);
  }

  private OptimizeIndexNameService getIndexNameService() {
    return getOptimizeOpenSearchClient().getIndexNameService();
  }

  @SneakyThrows
  private <T> List<T> getAllDocumentsOfIndicesAs(final String[] indexNames, final Class<T> type,
                                                 final Query query) {
    final SearchRequest.Builder searchReqBuilder = RequestDSL.searchRequestBuilder()
      .index(List.of(indexNames))
      .query(query)
      .trackTotalHits(new TrackHits.Builder().enabled(true).build())
      .size(100);

    String errorMessage = "Was not able to retrieve all documents for indices";
    SearchResponse<T> searchResponse = getOptimizeOpenSearchClient().search(searchReqBuilder, type, errorMessage);
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  private int getInstanceCountWithQuery(final Query query) {
    try {
      return Long.valueOf(getOptimizeOpenSearchClient()
                            .count(new String[]{PROCESS_INSTANCE_MULTI_ALIAS}, query)).intValue();
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate document count for index " + PROCESS_INSTANCE_MULTI_ALIAS,
        e
      );
    }
  }

  private Integer getVariableInstanceCountForAllProcessInstances(final Query processInstanceQuery) {
    // TODO implement with #11121
    throw new NotImplementedException("Not yet implemented for OpenSearch, will be implemented with issue #11121");
  }

  private void deleteIndexOfMapping(final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    deleteIndices(new String[]{indexMapping.getIndexName()});
  }

  @NotNull
  private static IndexRequest.Builder createIndexRequestBuilder(final String indexName, final String id, final Object entry) {
    IndexRequest.Builder request =
      new IndexRequest.Builder()
        .document(entry)
        .index(indexName)
        .id(id)
        .refresh(Refresh.True);
    return request;
  }

  private void deleteIndices(final String[] indicesToDelete) {
    getOptimizeOpenSearchClient()
      .getRichOpenSearchClient().index()
      .deleteIndicesWithRetries(indicesToDelete);
  }

  private void updateZeebeRecordsByQuery(final String zeebeRecordPrefix,
                                         final String indexName,
                                         final Query query,
                                         final String updateScript) {
    updateRecordsByQuery(zeebeRecordPrefix + "_" + indexName + "*", query, updateScript);
  }

  private void updateRecordsByQuery(final String indexName,
                                    final Query query,
                                    final String updateScript) {
    getOptimizeOpenSearchClient().getRichOpenSearchClient().doc()
      .updateByQuery(
        indexName,
        query,
        OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(updateScript, Collections.emptyMap())
      );
  }

}
