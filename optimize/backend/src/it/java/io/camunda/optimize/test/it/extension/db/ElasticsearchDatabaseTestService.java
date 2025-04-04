/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension.db;

import static io.camunda.optimize.ApplicationContextProvider.getBean;
import static io.camunda.optimize.service.db.DatabaseClient.convertToPrefixedAliasName;
import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil.mapHits;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
import static io.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsWithPercentileInterpolation;

import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.ValueCountAggregate;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.cluster.PutClusterSettingsRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.AliasDefinition;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.ExistsTemplateRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.get.Feature;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.json.JsonData;
import com.google.common.collect.Iterables;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeCountRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.schema.ElasticSearchIndexSettingsBuilder;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.TerminatedUserSessionIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleProcessReportIndexES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DatabaseHelper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.test.it.extension.MockServerUtil;
import io.camunda.optimize.test.repository.TestIndexRepositoryES;
import io.camunda.optimize.upgrade.es.ElasticsearchClientBuilder;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.tika.utils.StringUtils;
import org.elasticsearch.client.Request;
import org.mockserver.integration.ClientAndServer;

@Slf4j
public class ElasticsearchDatabaseTestService extends DatabaseTestService {
  private static final String MOCKSERVER_CLIENT_KEY = "MockServer";
  private static final Map<String, OptimizeElasticsearchClient> CLIENT_CACHE = new HashMap<>();
  private static final ClientAndServer mockServerClient = initMockServer();

  private String elasticsearchDatabaseVersion;

  private OptimizeElasticsearchClient optimizeElasticsearchClient;
  private final TaskRepositoryES taskRepositoryES;

  public ElasticsearchDatabaseTestService(
      final String customIndexPrefix, final boolean haveToClean) {
    super(customIndexPrefix, haveToClean);
    initEsClient();
    setTestIndexRepository(new TestIndexRepositoryES(optimizeElasticsearchClient));
    taskRepositoryES = new TaskRepositoryES(optimizeElasticsearchClient);
  }

  private static ClientAndServer initMockServer() {
    return DatabaseTestService.initMockServer(
        IntegrationTestConfigurationUtil.createItConfigurationService()
            .getElasticSearchConfiguration()
            .getFirstConnectionNode());
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
    if (optimizeElasticsearchClient == CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY)) {
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
      optimizeElasticsearchClient = CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY);
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
      getOptimizeElasticClient()
          .getEsClient()
          .indices()
          .refresh(
              RefreshRequest.of(
                  r ->
                      r.index(getIndexNameService().getIndexPrefix() + "*")
                          .allowNoIndices(true)
                          .ignoreUnavailable(true)
                          .expandWildcards(ExpandWildcard.Open)));
    } catch (final Exception e) {
      throw new OptimizeIntegrationTestException("Could not refresh Optimize indices!", e);
    }
  }

  @Override
  public void addEntryToDatabase(final String indexName, final String id, final Object entry) {
    try {
      getOptimizeElasticClient()
          .index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(getOptimizeElasticClient(), indexName)
                          .id(id)
                          .document(entry)
                          // necessary in order to search for the entry immediately
                          .refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException("Unable to add an entry to elasticsearch", e);
    }
  }

  @Override
  public void addEntriesToDatabase(final String indexName, final Map<String, Object> idToEntryMap) {
    StreamSupport.stream(Iterables.partition(idToEntryMap.entrySet(), 10_000).spliterator(), false)
        .forEach(
            batch -> {
              final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
              for (final Map.Entry<String, Object> idAndObject : batch) {
                bulkRequest.operations(
                    o ->
                        o.index(
                            OptimizeIndexOperationBuilderES.of(
                                i ->
                                    i.optimizeIndex(getOptimizeElasticClient(), indexName)
                                        .id(idAndObject.getKey())
                                        .document(idAndObject.getValue()))));
              }
              executeBulk(bulkRequest.build());
            });
  }

  @Override
  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type) {
    return getAllDocumentsOfIndexAs(indexName, type, Query.of(q -> q.matchAll(m -> m)));
  }

  @Override
  public DatabaseClient getDatabaseClient() {
    return optimizeElasticsearchClient;
  }

  @Override
  public Integer getDocumentCountOf(final String indexName) {
    try {
      final CountResponse countResponse =
          getOptimizeElasticClient()
              .count(
                  OptimizeCountRequestBuilderES.of(
                      c ->
                          c.optimizeIndex(getOptimizeElasticClient(), indexName)
                              .query(Query.of(q -> q.matchAll(m -> m)))));
      return Long.valueOf(countResponse.count()).intValue();
    } catch (final IOException | ElasticsearchException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot evaluate document count for index " + indexName, e);
    }
  }

  @Override
  public Integer getCountOfCompletedInstancesWithIdsIn(final Set<Object> processInstanceIds) {
    return getInstanceCountWithQuery(
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.filter(
                                f ->
                                    f.terms(
                                        t ->
                                            t.field(ProcessInstanceIndex.PROCESS_INSTANCE_ID)
                                                .terms(
                                                    tt ->
                                                        tt.value(
                                                            processInstanceIds.stream()
                                                                .map(FieldValue::of)
                                                                .toList()))))
                            .filter(f -> f.exists(e -> e.field(ProcessInstanceIndex.END_DATE))))));
  }

  @Override
  public void deleteAllOptimizeData() {
    try {
      getOptimizeElasticClient()
          .getEsClient()
          .deleteByQuery(
              DeleteByQueryRequest.of(
                  d ->
                      d.index(getIndexNameService().getIndexPrefix() + "*")
                          .query(Query.of(q -> q.matchAll(m -> m)))
                          .refresh(true)));
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
    try {
      getOptimizeElasticClient()
          .getEsClient()
          .deleteByQuery(
              DeleteByQueryRequest.of(
                  d ->
                      d.index(
                              getIndexNameService()
                                  .getOptimizeIndexAliasForIndex(new SingleProcessReportIndexES()))
                          .query(Query.of(q -> q.matchAll(m -> m)))
                          .refresh(true)));
    } catch (final IOException | ElasticsearchException e) {
      throw new OptimizeIntegrationTestException(
          "Could not delete data in index "
              + getIndexNameService()
                  .getOptimizeIndexAliasForIndex(new SingleProcessReportIndexES()),
          e);
    }
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

  @SneakyThrows
  @Override
  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    final String[] indicesToDelete =
        getOptimizeElasticClient()
            .getEsClient()
            .indices()
            .get(GetIndexRequest.of(r -> r.index("*")))
            .result()
            .keySet()
            .stream()
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
        getOptimizeElasticClient()
            .elasticsearchClient()
            .indices()
            .get(GetIndexRequest.of(r -> r.index("*")))
            .result()
            .keySet()
            .stream()
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
    getOptimizeElasticClient()
        .getEsClient()
        .updateByQuery(
            UpdateByQueryRequest.of(
                u ->
                    u.index(zeebeRecordPrefix + "_" + indexName + "*")
                        .refresh(true)
                        .script(
                            Script.of(i -> i.lang(ScriptLanguage.Painless).source(updateScript)))
                        .query(Query.of(q -> q.matchAll(m -> m)))));
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsWithPositionForPrefix(
      final String zeebeRecordPrefix,
      final String indexName,
      final long position,
      final String updateScript) {
    getOptimizeElasticClient()
        .getEsClient()
        .updateByQuery(
            UpdateByQueryRequest.of(
                u ->
                    u.index(zeebeRecordPrefix + "_" + indexName + "*")
                        .refresh(true)
                        .script(
                            Script.of(i -> i.lang(ScriptLanguage.Painless).source(updateScript)))
                        .query(
                            Query.of(
                                q ->
                                    q.bool(
                                        b ->
                                            b.must(
                                                m ->
                                                    m.term(
                                                        t ->
                                                            t.field(ZeebeRecordDto.Fields.position)
                                                                .value(position))))))));
  }

  @SneakyThrows
  @Override
  public void updateZeebeRecordsOfBpmnElementTypeForPrefix(
      final String zeebeRecordPrefix,
      final BpmnElementType bpmnElementType,
      final String updateScript) {
    getOptimizeElasticClient()
        .getEsClient()
        .updateByQuery(
            UpdateByQueryRequest.of(
                u ->
                    u.index(zeebeRecordPrefix + "_" + ZEEBE_PROCESS_INSTANCE_INDEX_NAME + "*")
                        .refresh(true)
                        .script(
                            Script.of(i -> i.lang(ScriptLanguage.Painless).source(updateScript)))
                        .query(
                            Query.of(
                                q ->
                                    q.bool(
                                        b ->
                                            b.must(
                                                m ->
                                                    m.term(
                                                        t ->
                                                            t.field(
                                                                    ZeebeRecordDto.Fields.value
                                                                        + "."
                                                                        + ZeebeProcessInstanceDataDto
                                                                            .Fields.bpmnElementType)
                                                                .value(
                                                                    bpmnElementType.name()))))))));
  }

  @SneakyThrows
  @Override
  public void updateUserTaskDurations(
      final String processInstanceId, final String processDefinitionKey, final long duration) {
    final String updateScript = buildUpdateScript(duration);
    getOptimizeElasticClient()
        .update(
            OptimizeUpdateRequestBuilderES.of(
                u ->
                    u.optimizeIndex(
                            getOptimizeElasticClient(),
                            getProcessInstanceIndexAliasName(processDefinitionKey))
                        .id(processInstanceId)
                        .script(
                            Script.of(i -> i.lang(ScriptLanguage.Painless).source(updateScript)))
                        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)),
            Object.class);
  }

  @Override
  public boolean indexExistsCheckWithApplyingOptimizePrefix(final String indexOrAliasName) {
    return indexExists(indexOrAliasName, false);
  }

  @Override
  @SneakyThrows
  public boolean indexExistsCheckWithoutApplyingOptimizePrefix(final String indexName) {
    final OptimizeElasticsearchClient esClient = getOptimizeElasticClient();
    return esClient
        .getEsClient()
        .indices()
        .exists(ExistsRequest.of(r -> r.index(indexName)))
        .value();
  }

  @SneakyThrows
  @Override
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(
      final String dbType, final String engine) {
    final GetResponse<TimestampBasedImportIndexDto> response =
        optimizeElasticsearchClient.get(
            OptimizeGetRequestBuilderES.of(
                r ->
                    r.optimizeIndex(optimizeElasticsearchClient, TIMESTAMP_BASED_IMPORT_INDEX_NAME)
                        .id(DatabaseHelper.constructKey(dbType, engine))),
            TimestampBasedImportIndexDto.class);
    if (response.found()) {
      return response.source().getTimestampOfLastEntity();
    } else {
      throw new NotFoundException(
          String.format(
              "Timestamp based import index does not exist: esType: {%s}, engine: {%s}",
              dbType, engine));
    }
  }

  @Override
  public Map<AggregationDto, Double> calculateExpectedValueGivenDurations(
      final Number... setDuration) {
    return calculateExpectedValueGivenDurationsWithPercentileInterpolation(setDuration);
  }

  @Override
  public long countRecordsByQuery(
      final TermsQueryContainer termsQueryContainer, final String expectedIndex) {
    return countRecordsByQuery(termsQueryContainer.toElasticSearchQuery(), expectedIndex);
  }

  @Override
  @SneakyThrows
  public <T> List<T> getZeebeExportedRecordsByQuery(
      final String exportIndex, final TermsQueryContainer query, final Class<T> zeebeRecordClass) {
    final OptimizeElasticsearchClient esClient = getOptimizeElasticClient();
    final Query boolQueryBuilder = query.toElasticSearchQuery();
    final SearchResponse<T> searchResponse =
        esClient.searchWithoutPrefixing(
            SearchRequest.of(
                s ->
                    s.index(exportIndex)
                        .query(boolQueryBuilder)
                        .trackTotalHits(t -> t.enabled(true))
                        .size(100)),
            zeebeRecordClass);
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), zeebeRecordClass, OPTIMIZE_MAPPER);
  }

  @Override
  @SneakyThrows
  public void deleteProcessInstancesFromIndex(final String indexName, final String id) {
    final DeleteRequest request =
        OptimizeDeleteRequestBuilderES.of(
            d ->
                d.optimizeIndex(getOptimizeElasticClient(), indexName)
                    .id(id)
                    .refresh(Refresh.True));
    getOptimizeElasticClient().delete(request);
  }

  @Override
  public DatabaseType getDatabaseVendor() {
    return DatabaseType.ELASTICSEARCH;
  }

  @Override
  @SneakyThrows
  protected <T extends OptimizeDto> List<T> getInstancesById(
      final String indexName,
      final List<String> instanceIds,
      final String idField,
      final Class<T> type) {
    final List<T> results = new ArrayList<>();

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(getOptimizeElasticClient(), indexName)
                    .query(
                        Query.of(
                            q ->
                                q.terms(
                                    t ->
                                        t.field(idField)
                                            .terms(
                                                tt ->
                                                    tt.value(
                                                        instanceIds.stream()
                                                            .map(FieldValue::of)
                                                            .toList())))))
                    .trackTotalHits(t -> t.enabled(true))
                    .size(100));

    final SearchResponse<T> searchResponse = getOptimizeElasticClient().search(searchRequest, type);
    for (final Hit<T> hit : searchResponse.hits().hits()) {
      results.add(hit.source());
    }
    return results;
  }

  @Override
  @SneakyThrows
  public <T> Optional<T> getDatabaseEntryById(
      final String indexName, final String entryId, final Class<T> type) {
    final GetRequest getRequest =
        OptimizeGetRequestBuilderES.of(
            r -> r.optimizeIndex(getOptimizeElasticClient(), indexName).id(entryId));
    final GetResponse<T> getResponse = getOptimizeElasticClient().get(getRequest, type);
    if (getResponse.found()) {
      return Optional.of(getResponse.source());
    } else {
      return Optional.empty();
    }
  }

  @Override
  @SneakyThrows
  public String getDatabaseVersion() {
    if (elasticsearchDatabaseVersion == null) {
      elasticsearchDatabaseVersion = getOptimizeElasticClient().getDatabaseVersion();
    }
    return elasticsearchDatabaseVersion;
  }

  @Override
  public int getNestedDocumentsLimit(final ConfigurationService configurationService) {
    return configurationService
        .getElasticSearchConfiguration()
        .getNestedDocumentsLimit()
        .intValue();
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
    final OptimizeElasticsearchClient esClient = getOptimizeElasticClient();
    final String indexName =
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionForAllIndicesOf(
                new ProcessInstanceIndexES(processDefinitionKey));

    esClient
        .elasticsearchClient()
        .indices()
        .putSettings(
            PutIndicesSettingsRequest.of(
                p ->
                    p.index(indexName)
                        .settings(
                            ElasticSearchIndexSettingsBuilder.buildDynamicSettings(
                                configurationService))));
  }

  @Override
  public void createIndex(
      final String optimizeIndexNameWithVersion, final String optimizeIndexAliasForIndex)
      throws IOException {
    final CreateIndexRequest request =
        CreateIndexRequest.of(
            i -> {
              i.index(optimizeIndexNameWithVersion);
              if (!StringUtils.isBlank(optimizeIndexAliasForIndex)) {
                i.aliases(optimizeIndexAliasForIndex, Alias.of(a -> a.isWriteIndex(true)));
              }
              return i;
            });

    getOptimizeElasticClient().elasticsearchClient().indices().create(request);
  }

  @Override
  public void createIndex(
      final String indexName,
      final Map<String, Boolean> aliases,
      final DefaultIndexMappingCreator indexMapping)
      throws IOException {
    final IndexSettings indexSettings =
        createIndexSettings(indexMapping, createConfigurationService());
    final CreateIndexRequest.Builder request = new CreateIndexRequest.Builder();
    request.index(convertToPrefixedAliasName(indexName, getOptimizeElasticClient()));
    for (final Map.Entry<String, Boolean> alias : aliases.entrySet()) {
      request.aliases(alias.getKey(), Alias.of(a -> a.isWriteIndex(alias.getValue())));
    }
    request.settings(indexSettings);
    request.mappings(indexMapping.getSource());
    indexMapping.setDynamic(DynamicMapping.False);
    getOptimizeElasticClient().elasticsearchClient().indices().create(request.build());
  }

  @Override
  public Optional<MetadataDto> readMetadata() {
    return getBean(ElasticSearchMetadataService.class).readMetadata(getOptimizeElasticClient());
  }

  @Override
  public void setActivityStartDatesToNull(
      final String processDefinitionKey, final ScriptData scriptData) {
    final UpdateByQueryRequest.Builder request =
        new UpdateByQueryRequest.Builder()
            .conflicts(Conflicts.Proceed)
            .query(Query.of(q -> q.matchAll(m -> m)))
            .script(
                Script.of(
                    i ->
                        i.lang(ScriptLanguage.Painless)
                            .source(scriptData.scriptString())
                            .params(
                                scriptData.params().entrySet().stream()
                                    .collect(
                                        Collectors.toMap(
                                            Map.Entry::getKey, e -> JsonData.of(e.getValue()))))))
            .refresh(true);

    try {
      getOptimizeElasticClient()
          .updateByQuery(request, List.of(getProcessInstanceIndexAliasName(processDefinitionKey)));
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException("Could not set activity start dates to null.", e);
    }
  }

  @Override
  public void setUserTaskDurationToNull(
      final String processInstanceId, final String durationFieldName, final ScriptData script) {
    final UpdateByQueryRequest.Builder request =
        new UpdateByQueryRequest.Builder()
            .conflicts(Conflicts.Proceed)
            .query(
                Query.of(
                    q ->
                        q.bool(
                            b ->
                                b.must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(PROCESS_INSTANCE_ID)
                                                    .value(processInstanceId))))))
            .script(
                Script.of(
                    i ->
                        i.lang(ScriptLanguage.Painless)
                            .source(script.scriptString())
                            .params(
                                script.params().entrySet().stream()
                                    .collect(
                                        Collectors.toMap(
                                            Map.Entry::getKey, e -> JsonData.of(e.getValue()))))))
            .refresh(true);

    try {
      getOptimizeElasticClient().updateByQuery(request, List.of(PROCESS_INSTANCE_MULTI_ALIAS));
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(
          String.format("Could not set userTask duration field [%s] to null.", durationFieldName),
          e);
    }
  }

  @Override
  @SneakyThrows
  public Long getImportedActivityCount() {
    final SearchResponse<?> response =
        getOptimizeElasticClient()
            .search(
                OptimizeSearchRequestBuilderES.of(
                    s ->
                        s.optimizeIndex(getOptimizeElasticClient(), PROCESS_INSTANCE_MULTI_ALIAS)
                            .query(q -> q.matchAll(m -> m))
                            .size(0)
                            .source(ss -> ss.fetch(false))
                            .aggregations(
                                FLOW_NODE_INSTANCES,
                                Aggregation.of(
                                    a ->
                                        a.nested(n -> n.path(FLOW_NODE_INSTANCES))
                                            .aggregations(
                                                FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION,
                                                Aggregation.of(
                                                    aa ->
                                                        aa.valueCount(
                                                            c ->
                                                                c.field(
                                                                    FLOW_NODE_INSTANCES
                                                                        + "."
                                                                        + ProcessInstanceIndex
                                                                            .FLOW_NODE_INSTANCE_ID))))))),
                Object.class);

    final NestedAggregate nested = response.aggregations().get(FLOW_NODE_INSTANCES).nested();
    final ValueCountAggregate countAggregator =
        nested.aggregations().get(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION).valueCount();
    return Double.valueOf(countAggregator.value()).longValue();
  }

  @Override
  @SneakyThrows
  public List<String> getAllIndicesWithWriteAlias(final String aliasNameWithPrefix) {
    final GetAliasRequest aliasesRequest =
        GetAliasRequest.of(
            a -> a.index(getOptimizeElasticClient().addPrefixesToIndices(aliasNameWithPrefix)));
    final Map<String, IndexAliases> indexNameToAliasMap =
        getOptimizeElasticClient().getAlias(aliasesRequest).result();
    return indexNameToAliasMap.keySet().stream()
        .filter(
            index ->
                indexNameToAliasMap.get(index).aliases().entrySet().stream()
                    .anyMatch(a -> a.getValue().isWriteIndex()))
        .toList();
  }

  @Override
  public VariableUpdateInstanceIndex getVariableUpdateInstanceIndex() {
    return new VariableUpdateInstanceIndexES();
  }

  @Override
  public void deleteAllDocumentsInIndex(final String optimizeIndexAliasForIndex) {
    try {
      getOptimizeElasticClient()
          .elasticsearchClient()
          .deleteByQuery(
              DeleteByQueryRequest.of(
                  r ->
                      r.index(
                              convertToPrefixedAliasName(
                                  optimizeIndexAliasForIndex, getOptimizeElasticClient()))
                          .query(q -> q.matchAll(m -> m))
                          .refresh(true)));
    } catch (final IOException | ElasticsearchException e) {
      throw new OptimizeIntegrationTestException(
          "Could not delete data in index " + optimizeIndexAliasForIndex, e);
    }
  }

  @Override
  public void insertTestDocuments(
      final int amount, final String indexName, final String jsonDocument) throws IOException {
    getOptimizeElasticClient()
        .bulk(
            BulkRequest.of(
                r -> {
                  for (int i = 0; i < amount; i++) {
                    final int finalI = i;
                    r.operations(
                        o ->
                            o.index(
                                OptimizeIndexOperationBuilderES.of(
                                    b ->
                                        b.optimizeIndex(getOptimizeElasticClient(), indexName)
                                            .document(
                                                JsonData.fromJson(
                                                    String.format(jsonDocument, finalI))))));
                  }
                  return r;
                }));
    getOptimizeElasticClient().refresh(indexName);
  }

  @Override
  public void performLowLevelBulkRequest(
      final String methodName, final String endpoint, final String bulkPayload) throws IOException {
    final HttpEntity entity = new NStringEntity(bulkPayload, ContentType.APPLICATION_JSON);
    final Request request = new Request(methodName, endpoint);
    request.setEntity(entity);
    getOptimizeElasticClient().performRequest(request);
  }

  @Override
  public void initSchema(final DatabaseSchemaManager schemaManager) {
    schemaManager.initializeSchema(getOptimizeElasticClient());
  }

  @Override
  public Map<String, ? extends Object> getMappingFields(final String indexName) throws IOException {
    final GetMappingResponse getMappingResponse =
        getOptimizeElasticClient().getMapping(new GetMappingRequest.Builder(), indexName);
    final Object propertiesMap =
        getMappingResponse.result().values().stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new OptimizeRuntimeException(
                        "There should be at least one mapping available for the index!"))
            .mappings()
            .properties();
    if (propertiesMap instanceof Map) {
      return (Map<String, Object>) propertiesMap;
    } else {
      throw new OptimizeRuntimeException(
          "ElasticSearch index mapping properties should be of type map");
    }
  }

  @Override
  public boolean indexExists(final String indexOrAliasName, final Boolean addMappingFeatures) {
    final GetIndexRequest.Builder request = new GetIndexRequest.Builder();
    request.index(getOptimizeElasticClient().addPrefixesToIndices(indexOrAliasName));
    if (addMappingFeatures) {
      request.features(Feature.Mappings);
    }
    try {
      return getOptimizeElasticClient().exists(request.build());
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not check if [%s] index already exist.", String.join(",", indexOrAliasName));
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public boolean templateExists(final String optimizeIndexTemplateNameWithVersion)
      throws IOException {
    return getOptimizeElasticClient()
        .elasticsearchClient()
        .indices()
        .existsTemplate(ExistsTemplateRequest.of(e -> e.name(optimizeIndexTemplateNameWithVersion)))
        .value();
  }

  @Override
  public boolean isAliasReadOnly(final String readOnlyAliasForIndex) throws IOException {
    final GetAliasResponse aliases =
        getOptimizeElasticClient()
            .getAlias(
                GetAliasRequest.of(
                    a ->
                        a.name(
                            getOptimizeElasticClient()
                                .addPrefixesToIndices(readOnlyAliasForIndex))));
    return aliases.result().values().stream()
        .flatMap(a -> a.aliases().values().stream())
        .collect(Collectors.toSet())
        .stream()
        .noneMatch(AliasDefinition::isWriteIndex);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return getOptimizeElasticClient().getIndexNameService();
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return optimizeElasticsearchClient;
  }

  private long countRecordsByQuery(final Query boolQueryBuilder, final String expectedIndex) {
    final OptimizeElasticsearchClient esClient = getOptimizeElasticClient();
    final CountRequest countRequest =
        CountRequest.of(c -> c.index(expectedIndex).query(boolQueryBuilder));
    try {
      return esClient.elasticsearchClient().count(countRequest).count();
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
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

  private void initEsClient() {
    if (CLIENT_CACHE.containsKey(customIndexPrefix)) {
      optimizeElasticsearchClient = CLIENT_CACHE.get(customIndexPrefix);
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
    optimizeElasticsearchClient =
        new OptimizeElasticsearchClient(
            ElasticsearchClientBuilder.restClient(configurationService, new PluginRepository()),
            OPTIMIZE_MAPPER,
            ElasticsearchClientBuilder.build(
                configurationService, OPTIMIZE_MAPPER, new PluginRepository()),
            new OptimizeIndexNameService(configurationService, DatabaseType.ELASTICSEARCH));
    adjustClusterSettings();
    CLIENT_CACHE.put(clientKey, optimizeElasticsearchClient);
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
    final PutClusterSettingsRequest clusterUpdateSettingsRequest =
        PutClusterSettingsRequest.of(
            p ->
                // we allow auto index creation because the Zeebe exporter creates indices for
                // records
                p.persistent("action.auto_create_index", JsonData.of(true))
                    // all of our tests are running against a one node cluster.
                    // Since we're creating a lot of indexes, we are easily hitting the default
                    // value of 1000. Thus, we need to increase this value for the test setup.
                    .persistent("cluster.max_shards_per_node", JsonData.of(10000))
                    .flatSettings(true));
    try {
      optimizeElasticsearchClient
          .elasticsearchClient()
          .cluster()
          .putSettings(clusterUpdateSettingsRequest);
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
      final String indexName, final Class<T> type, final Query query) {
    try {
      return getAllDocumentsOfIndicesAs(new String[] {indexName}, type, query);
    } catch (final ElasticsearchException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot get all documents for index " + indexName, e);
    }
  }

  @SneakyThrows
  private <T> List<T> getAllDocumentsOfIndicesAs(
      final String[] indexNames, final Class<T> type, final Query query) {

    final List<T> results = new ArrayList<>();
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
      final SearchResponse<T> response =
          getOptimizeElasticClient()
              .searchWithoutPrefixing(
                  SearchRequest.of(
                      s ->
                          s.index(List.of(indexNames))
                              .query(query)
                              .trackTotalHits(t -> t.enabled(true))
                              .size(100)),
                  type);
      results.addAll(mapHits(response.hits(), type, getObjectMapper()));
    }

    if (groupedByPrefix.containsKey("OptimizeIndex")) {
      final SearchResponse<T> response =
          getOptimizeElasticClient()
              .search(
                  OptimizeSearchRequestBuilderES.of(
                      s ->
                          s.optimizeIndex(getOptimizeElasticClient(), indexNames)
                              .query(query)
                              .trackTotalHits(t -> t.enabled(true))
                              .size(100)),
                  type);
      results.addAll(mapHits(response.hits(), type, getObjectMapper()));
    }

    return results;
  }

  private int getInstanceCountWithQuery(final Query query) {
    try {
      final CountResponse countResponse =
          getOptimizeElasticClient()
              .count(
                  OptimizeCountRequestBuilderES.of(
                      c ->
                          c.optimizeIndex(getOptimizeElasticClient(), PROCESS_INSTANCE_MULTI_ALIAS)
                              .query(query)));
      return Long.valueOf(countResponse.count()).intValue();
    } catch (final IOException | ElasticsearchException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot evaluate document count for index " + PROCESS_INSTANCE_MULTI_ALIAS, e);
    }
  }

  private Integer getVariableInstanceCountForAllProcessInstances(final Query processInstanceQuery) {
    final SearchResponse<?> searchResponse;
    try {
      searchResponse =
          getOptimizeElasticClient()
              .search(
                  OptimizeSearchRequestBuilderES.of(
                      s ->
                          s.optimizeIndex(getOptimizeElasticClient(), PROCESS_INSTANCE_MULTI_ALIAS)
                              .query(Query.of(q -> q.matchAll(m -> m)))
                              .size(0)
                              .source(ss -> ss.fetch(false))
                              .aggregations(
                                  FLOW_NODE_INSTANCES,
                                  Aggregation.of(
                                      a ->
                                          a.nested(n -> n.path(FLOW_NODE_INSTANCES))
                                              .aggregations(
                                                  FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION,
                                                  Aggregation.of(
                                                      aa ->
                                                          aa.valueCount(
                                                              v ->
                                                                  v.field(
                                                                      FLOW_NODE_INSTANCES
                                                                          + "."
                                                                          + ProcessInstanceIndex
                                                                              .FLOW_NODE_INSTANCE_ID))))))),
                  Object.class);
    } catch (final IOException | ElasticsearchException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot evaluate variable instance count in process instance indices.", e);
    }

    final NestedAggregate nestedAgg = searchResponse.aggregations().get(VARIABLES).nested();
    final ValueCountAggregate countAggregator = nestedAgg.aggregations().get("count").valueCount();
    final double totalVariableCount = countAggregator.value();

    return Double.valueOf(totalVariableCount).intValue();
  }

  private void deleteIndexOfMapping(final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    getOptimizeElasticClient().deleteIndex(indexMapping);
  }

  private IndexSettings createIndexSettings(
      final IndexMappingCreator indexMappingCreator,
      final ConfigurationService configurationService) {
    try {
      return ElasticSearchIndexSettingsBuilder.buildAllSettings(
          configurationService, indexMappingCreator);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }
}
