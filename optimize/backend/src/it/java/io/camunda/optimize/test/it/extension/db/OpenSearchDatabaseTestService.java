/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension.db;

import static io.camunda.optimize.ApplicationContextProvider.getBean;
import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager.createIndexFromJson;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static io.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsWithPercentileInterpolation;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.util.ContentType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Iterables;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.schema.TransportOptionsProvider;
import io.camunda.optimize.service.db.os.ExtendedOpenSearchClient;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.builders.OptimizeIndexOperationOS;
import io.camunda.optimize.service.db.os.client.dsl.AggregationDSL;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.schema.OpenSearchIndexSettingsBuilder;
import io.camunda.optimize.service.db.os.schema.OpenSearchMetadataService;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.schema.index.ExternalProcessVariableIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.TerminatedUserSessionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.VariableUpdateInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleProcessReportIndexOS;
import io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
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
import io.camunda.optimize.test.repository.TestIndexRepositoryOS;
import io.camunda.optimize.upgrade.os.OpenSearchClientBuilder;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.http.HttpEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.jetbrains.annotations.NotNull;
import org.mockserver.integration.ClientAndServer;
import org.opensearch.client.Request;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.ValueCountAggregate;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
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
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.AliasDefinition;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.ExistsTemplateRequest;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.indices.GetMappingRequest;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.opensearch.client.opensearch.snapshot.CreateRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.slf4j.Logger;

public class OpenSearchDatabaseTestService extends DatabaseTestService {

  private static final String MOCKSERVER_CLIENT_KEY = "MockServer";
  private static final Map<String, OptimizeOpenSearchClient> CLIENT_CACHE = new HashMap<>();
  private static final ClientAndServer mockServerClient = initMockServer();
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OpenSearchDatabaseTestService.class);

  private String opensearchDatabaseVersion;

  private OptimizeOpenSearchClient prefixAwareOptimizeOpenSearchClient;
  private ExtendedOpenSearchClient extendedOpenSearchClient;

  public OpenSearchDatabaseTestService(final String customIndexPrefix, final boolean haveToClean) {
    super(customIndexPrefix, haveToClean);
    initOsClient();
    setTestIndexRepository(new TestIndexRepositoryOS(prefixAwareOptimizeOpenSearchClient));
  }

  private static ClientAndServer initMockServer() {
    return DatabaseTestService.initMockServer(
        IntegrationTestConfigurationUtil.createItConfigurationService()
            .getOpenSearchConfiguration()
            .getFirstConnectionNode());
  }

  @NotNull
  private static IndexRequest.Builder createIndexRequestBuilder(
      final String indexName, final String id, final Object entry) {
    final IndexRequest.Builder request =
        new IndexRequest.Builder().document(entry).index(indexName).id(id).refresh(Refresh.True);
    return request;
  }

  @Override
  public void beforeEach() {
    if (haveToClean) {
      LOG.info("Cleaning database...");
      cleanAndVerifyDatabase();
      LOG.info("All documents have been wiped out! Database has successfully been cleaned!");
    }
  }

  @Override
  public void afterEach() {
    // If the MockServer has been used, we reset all expectations and logs and revert to the default
    // client
    if (prefixAwareOptimizeOpenSearchClient == CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY)) {
      LOG.info("Resetting all MockServer expectations and logs");
      mockServerClient.reset();
      LOG.info("No longer using OS MockServer");
      initOsClient();
    }
  }

  @Override
  public ClientAndServer useDBMockServer() {
    LOG.info("Using OpenSearch MockServer");
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
      final RefreshRequest refreshAllIndicesRequest =
          new RefreshRequest.Builder().index(getIndexNameService().getIndexPrefix() + "*").build();

      getOptimizeOpenSearchClient()
          .getOpenSearchClient()
          .indices()
          .refresh(refreshAllIndicesRequest);
    } catch (final Exception e) {
      throw new OptimizeIntegrationTestException("Could not refresh Optimize indices!", e);
    }
  }

  @Override
  public void addEntryToDatabase(final String indexName, final String id, final Object entry) {
    final IndexRequest.Builder request = createIndexRequestBuilder(indexName, id, entry);
    final IndexResponse response =
        getOptimizeOpenSearchClient().getRichOpenSearchClient().doc().index(request);
    if (!response.shards().failures().isEmpty()) {
      final String reason =
          String.format(
              "Could not add entry to index %s with id %s and entry %s", indexName, id, entry);
      throw new OptimizeIntegrationTestException(reason);
    }
  }

  @Override
  public void addEntriesToDatabase(final String indexName, final Map<String, Object> idToEntryMap) {
    StreamSupport.stream(Iterables.partition(idToEntryMap.entrySet(), 10_000).spliterator(), false)
        .forEach(
            batch -> {
              final List<BulkOperation> operations = new ArrayList<>();
              for (final Map.Entry<String, Object> idAndObject : batch) {
                final IndexOperation.Builder<Object> operation =
                    new IndexOperation.Builder<>()
                        .document(idAndObject.getValue())
                        .id(idAndObject.getKey());
                operations.add(operation.build()._toBulkOperation());
              }
              prefixAwareOptimizeOpenSearchClient.doBulkRequest(
                  () -> new BulkRequest.Builder().operations(operations).index(indexName),
                  operations,
                  "add entries",
                  false);
            });
  }

  @Override
  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type) {
    return getAllDocumentsOfIndexAs(indexName, type, QueryDSL.matchAll());
  }

  @Override
  public DatabaseClient getDatabaseClient() {
    return prefixAwareOptimizeOpenSearchClient;
  }

  @Override
  public Integer getDocumentCountOf(final String indexName) {
    try {
      return Long.valueOf(
              getOptimizeOpenSearchClient().count(new String[] {indexName}, QueryDSL.matchAll()))
          .intValue();
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot evaluate document count for index " + indexName, e);
    }
  }

  @Override
  public void deleteAllOptimizeData() {
    try {
      getOptimizeOpenSearchClient()
          .deleteByQuery(QueryDSL.matchAll(), true, getIndexNameService().getIndexPrefix() + "*");
    } catch (final Exception e) {
      // Not a problem if the deletion fails
    }
  }

  @Override
  public void deleteAllIndicesContainingTerm(final String indexTerm) {
    getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .index()
        .deleteIndicesWithRetries(indexTerm + "*");
  }

  @Override
  public void deleteAllSingleProcessReports() {
    getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .doc()
        .deleteByQuery(
            QueryDSL.matchAll(),
            true,
            getIndexNameService().getOptimizeIndexAliasForIndex(new SingleProcessReportIndexOS()));
  }

  @Override
  public void deleteTerminatedSessionsIndex() {
    deleteIndexOfMapping(new TerminatedUserSessionIndexOS());
  }

  @Override
  public void deleteAllVariableUpdateInstanceIndices() {
    final String[] indexNames =
        getOptimizeOpenSearchClient()
            .getAllIndicesForAlias(
                getIndexNameService()
                    .getOptimizeIndexAliasForIndex(new VariableUpdateInstanceIndexOS()))
            .toArray(String[]::new);
    deleteIndices(indexNames);
  }

  @Override
  public void deleteAllExternalVariableIndices() {
    final String[] indexNames =
        getOptimizeOpenSearchClient()
            .getAllIndicesForAlias(
                getIndexNameService()
                    .getOptimizeIndexAliasForIndex(new ExternalProcessVariableIndexOS()))
            .toArray(String[]::new);
    deleteIndices(indexNames);
  }

  @Override
  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    final GetIndexResponse allIndices =
        getOptimizeOpenSearchClient()
            .getRichOpenSearchClient()
            .index()
            .get(RequestDSL.getIndexRequestBuilder("*").ignoreUnavailable(true));

    final String[] indicesToDelete =
        allIndices.result().keySet().stream()
            .filter(indexName -> indexName.contains(zeebeRecordPrefix))
            .toArray(String[]::new);

    if (indicesToDelete.length > 1) {
      deleteIndices(indicesToDelete);
    }
  }

  @Override
  public void deleteAllOtherZeebeRecordsWithPrefix(
      final String zeebeRecordPrefix, final String recordsToKeep) {
    // Since we are retrieving zeebe records, we cannot use the rich opensearch client,
    // because it will add optimize prefixes to the request
    final GetIndexResponse allIndices;
    try {
      allIndices =
          getOptimizeOpenSearchClient()
              .getOpenSearchClient()
              .indices()
              .get(RequestDSL.getIndexRequestBuilder("*").ignoreUnavailable(true).build());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    final String[] indicesToDelete =
        allIndices.result().keySet().stream()
            .filter(
                indexName ->
                    indexName.contains(zeebeRecordPrefix) && !indexName.contains(recordsToKeep))
            .toArray(String[]::new);

    if (indicesToDelete.length > 1) {
      deleteIndices(indicesToDelete);
    }
  }

  @Override
  public void updateZeebeRecordsForPrefix(
      final String zeebeRecordPrefix, final String indexName, final String updateScript) {
    updateZeebeRecordsByQuery(zeebeRecordPrefix, indexName, QueryDSL.matchAll(), updateScript);
  }

  @Override
  public void updateZeebeRecordsWithPositionForPrefix(
      final String zeebeRecordPrefix,
      final String indexName,
      final long position,
      final String updateScript) {
    updateZeebeRecordsByQuery(
        zeebeRecordPrefix,
        indexName,
        QueryDSL.term(ZeebeRecordDto.Fields.position, position),
        updateScript);
  }

  @Override
  public void updateZeebeRecordsOfBpmnElementTypeForPrefix(
      final String zeebeRecordPrefix,
      final BpmnElementType bpmnElementType,
      final String updateScript) {
    updateZeebeRecordsByQuery(
        zeebeRecordPrefix,
        ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
        QueryDSL.term(
            ZeebeRecordDto.Fields.value + "." + ZeebeProcessInstanceDataDto.Fields.bpmnElementType,
            bpmnElementType.name()),
        updateScript);
  }

  @Override
  public void updateUserTaskDurations(
      final String processInstanceId, final String processDefinitionKey, final long duration) {
    final String updateScript = buildUpdateScript(duration);
    updateRecordsByQuery(
        getProcessInstanceIndexAliasName(processDefinitionKey),
        QueryDSL.ids(processInstanceId),
        updateScript);
  }

  @Override
  public boolean indexExistsCheckWithApplyingOptimizePrefix(final String indexOrAliasName) {
    return getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .index()
        .indexExists(indexOrAliasName);
  }

  @Override
  public boolean indexExistsCheckWithoutApplyingOptimizePrefix(final String indexName) {
    // Cannot use the rich client here because we need an unprefixed index name
    try {
      return getOptimizeOpenSearchClient()
          .getOpenSearchClient()
          .indices()
          .exists(new ExistsRequest.Builder().index(indexName).build())
          .value();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  @Override
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(
      final String dbType, final String engine) {
    final Optional<TimestampBasedImportIndexDto> response =
        prefixAwareOptimizeOpenSearchClient
            .getRichOpenSearchClient()
            .doc()
            .getRequest(
                TIMESTAMP_BASED_IMPORT_INDEX_NAME,
                DatabaseHelper.constructKey(dbType, engine),
                TimestampBasedImportIndexDto.class);

    return response
        .map(TimestampBasedImportIndexDto::getTimestampOfLastEntity)
        .orElseThrow(
            () ->
                new OptimizeIntegrationTestException(
                    String.format(
                        "Timestamp based import index does not exist: dbType: {%s}, engine: {%s}",
                        dbType, engine)));
  }

  @Override
  public Map<AggregationDto, Double> calculateExpectedValueGivenDurations(
      final Number... setDuration) {
    return calculateExpectedValueGivenDurationsWithPercentileInterpolation(setDuration);
  }

  @Override
  public long countRecordsByQuery(
      final TermsQueryContainer queryContainer, final String expectedIndex) {
    try {
      return getOptimizeOpenSearchClient()
          .count(new String[] {expectedIndex}, queryContainer.toOpenSearchQuery().toQuery());
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  @Override
  public <T> List<T> getZeebeExportedRecordsByQuery(
      final String exportIndex,
      final TermsQueryContainer queryForZeebeRecords,
      final Class<T> zeebeRecordClass) {
    final BoolQuery query = queryForZeebeRecords.toOpenSearchQuery();
    final SearchRequest.Builder searchRequest =
        RequestDSL.searchRequestBuilder().index(exportIndex).query(query.toQuery()).size(100);
    try {
      return getOptimizeOpenSearchClient()
          .getOpenSearchClient()
          .search(searchRequest.build(), zeebeRecordClass)
          .hits()
          .hits()
          .stream()
          .map(Hit::source)
          .toList();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  @Override
  public void deleteProcessInstancesFromIndex(final String indexName, final String id) {
    getOptimizeOpenSearchClient().getRichOpenSearchClient().doc().delete(indexName, id);
  }

  @Override
  public DatabaseType getDatabaseVendor() {
    return DatabaseType.OPENSEARCH;
  }

  @Override
  public void createSnapshot(
      final String snapshotRepositoryName, final String snapshotName, final String[] indexNames) {
    final CreateSnapshotRequest createSnapshotRequest =
        CreateSnapshotRequest.of(
            b ->
                b.repository(snapshotRepositoryName)
                    .snapshot(snapshotName)
                    .indices(Arrays.stream(indexNames).toList())
                    .includeGlobalState(false)
                    .waitForCompletion(true));
    try {
      getOptimizeOpenSearchClient()
          .triggerSnapshotAsync(createSnapshotRequest)
          .get(10, TimeUnit.SECONDS);
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      throw new OptimizeRuntimeException("Exception during creation snapshot:", e);
    }
  }

  @Override
  public void createRepoSnapshot(final String snapshotRepositoryName) {
    try {
      getOptimizeOpenSearchClient()
          .getOpenSearchClient()
          .snapshot()
          .createRepository(
              CreateRepositoryRequest.of(
                  b ->
                      b.name(snapshotRepositoryName)
                          .settings(s -> s.location("/var/tmp"))
                          .type("fs")));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void cleanSnapshots(final String snapshotRepositoryName) {
    try {
      getOptimizeOpenSearchClient()
          .getOpenSearchClient()
          .snapshot()
          .delete(
              DeleteSnapshotRequest.of(b -> b.repository(snapshotRepositoryName).snapshot("*")));
      getOptimizeOpenSearchClient()
          .getOpenSearchClient()
          .snapshot()
          .deleteRepository(DeleteRepositoryRequest.of(b -> b.name(snapshotRepositoryName)));
    } catch (final Exception e) {
      LOG.warn("Delete failed, no snapshots to delete from repository {}", snapshotRepositoryName);
    }
  }

  @Override
  public List<String> getImportIndices() {
    return OpenSearchSchemaManager.getAllNonDynamicMappings().stream()
        .filter(IndexMappingCreator::isImportIndex)
        .map(IndexMappingCreator::getIndexName)
        .toList();
  }

  @Override
  protected <T extends OptimizeDto> List<T> getInstancesById(
      final String indexName,
      final List<String> instanceIds,
      final String idField,
      final Class<T> type) {
    return getAllDocumentsOfIndicesAs(
        new String[] {indexName}, type, QueryDSL.stringTerms(idField, instanceIds));
  }

  @Override
  public <T> Optional<T> getDatabaseEntryById(
      final String indexName, final String entryId, final Class<T> type) {
    return Optional.ofNullable(
        getOptimizeOpenSearchClient()
            .get(
                RequestDSL.getRequest(indexName, entryId),
                type,
                "Could not retrieve entry from index " + indexName + " with id " + entryId)
            .source());
  }

  @Override
  public String getDatabaseVersion() {
    if (opensearchDatabaseVersion == null) {
      try {
        opensearchDatabaseVersion =
            OpenSearchClientBuilder.getCurrentOSVersion(
                getOptimizeOpenSearchClient().getOpenSearchClient());
      } catch (final IOException e) {
        throw new OptimizeRuntimeException(e);
      }
    }
    return opensearchDatabaseVersion;
  }

  @Override
  public int getNestedDocumentsLimit(final ConfigurationService configurationService) {
    return configurationService.getOpenSearchConfiguration().getNestedDocumentsLimit();
  }

  @Override
  public void setNestedDocumentsLimit(
      final ConfigurationService configurationService, final int nestedDocumentsLimit) {
    configurationService.getOpenSearchConfiguration().setNestedDocumentsLimit(nestedDocumentsLimit);
  }

  @Override
  public void updateProcessInstanceNestedDocLimit(
      final String processDefinitionKey,
      final int nestedDocLimit,
      final ConfigurationService configurationService) {
    setNestedDocumentsLimit(configurationService, nestedDocLimit);
    final OptimizeOpenSearchClient osClient = getOptimizeOpenSearchClient();
    final String indexName =
        osClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionForAllIndicesOf(
                new ProcessInstanceIndexOS(processDefinitionKey));
    try {
      osClient
          .getRichOpenSearchClient()
          .index()
          .putSettings(
              new PutIndicesSettingsRequest.Builder()
                  .settings(
                      OpenSearchIndexSettingsBuilder.buildDynamicSettings(configurationService))
                  .index(indexName)
                  .build());
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  @Override
  public void createIndex(
      final String optimizeIndexNameWithVersion, final String optimizeIndexAliasForIndex)
      throws IOException {
    final HashMap<String, Alias> aliasData = new HashMap<>();
    aliasData.put(optimizeIndexAliasForIndex, new Alias.Builder().isWriteIndex(true).build());
    final CreateIndexRequest request =
        new CreateIndexRequest.Builder()
            .index(optimizeIndexNameWithVersion)
            .aliases(aliasData)
            .build();
    final boolean created =
        getOptimizeOpenSearchClient().getRichOpenSearchClient().index().createIndex(request);
    if (!created) {
      throw new IOException("Could not create index " + optimizeIndexNameWithVersion);
    }
  }

  @Override
  public void createIndex(
      final String indexName,
      final Map<String, Boolean> aliases,
      final DefaultIndexMappingCreator indexMapping)
      throws IOException {

    final IndexSettings indexSettings =
        createIndexSettings(indexMapping, createConfigurationService());

    final HashMap<String, Alias> aliasData = new HashMap<>();
    for (final Map.Entry<String, Boolean> entry : aliases.entrySet()) {
      aliasData.put(entry.getKey(), new Alias.Builder().isWriteIndex(entry.getValue()).build());
    }

    indexMapping.setDynamic(DynamicMapping.False);
    final CreateIndexRequest request =
        createIndexFromJson(
            indexMapping.getSource().toString(), indexName, aliasData, indexSettings);
    final boolean created =
        getOptimizeOpenSearchClient().getRichOpenSearchClient().index().createIndex(request);
    if (!created) {
      throw new IOException("Could not create index " + indexName);
    }
  }

  @Override
  public Optional<MetadataDto> readMetadata() {
    return getBean(OpenSearchMetadataService.class).readMetadata(getOptimizeOpenSearchClient());
  }

  @Override
  public void setActivityStartDatesToNull(
      final String processDefinitionKey, final ScriptData script) {
    getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .doc()
        .updateByQuery(
            getProcessInstanceIndexAliasName(processDefinitionKey),
            QueryDSL.matchAll(),
            QueryDSL.script(script.scriptString(), script.params()));
  }

  @Override
  public void setUserTaskDurationToNull(
      final String processInstanceId, final String durationFieldName, final ScriptData script) {
    getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .doc()
        .updateByQuery(
            PROCESS_INSTANCE_MULTI_ALIAS,
            QueryDSL.term(PROCESS_INSTANCE_ID, processInstanceId),
            QueryDSL.script(script.scriptString(), script.params()));
  }

  @Override
  public Long getImportedActivityCount() {
    final Aggregation subAggregation =
        AggregationDSL.valueCountAggregation(
                String.join(".", FLOW_NODE_INSTANCES, ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID))
            ._toAggregation();
    final NestedAggregation termsAgg =
        new NestedAggregation.Builder().path(FLOW_NODE_INSTANCES).build();
    final Aggregation agg =
        AggregationDSL.withSubaggregations(
            termsAgg, Map.of(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION, subAggregation));

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(PROCESS_INSTANCE_MULTI_ALIAS)
            .query(QueryDSL.matchAll())
            .size(0)
            .aggregations(Map.of(FLOW_NODE_INSTANCES, agg));
    final SearchResponse<Aggregate> searchResponse =
        getOptimizeOpenSearchClient()
            .search(
                searchRequest,
                Aggregate.class,
                "Could not retrieve activity count from process instance indices.");
    final Aggregate nested = searchResponse.aggregations().get(FLOW_NODE_INSTANCES);
    final ValueCountAggregate countAggregator =
        nested
            .nested()
            .aggregations()
            .get(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION)
            .valueCount();
    return (long) countAggregator.value();
  }

  @Override
  public List<String> getAllIndicesWithWriteAlias(final String aliasNameWithPrefix) {
    final GetAliasResponse aliasResponse;
    try {
      aliasResponse = getOptimizeOpenSearchClient().getAlias(aliasNameWithPrefix);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
    final Map<String, IndexAliases> indexNameToAliasMap = aliasResponse.result();
    return indexNameToAliasMap.entrySet().stream()
        .filter(
            entry ->
                entry.getValue().aliases().values().stream()
                    .anyMatch(alias -> alias.isWriteIndex() != null && alias.isWriteIndex()))
        .map(Map.Entry::getKey)
        .toList();
  }

  @Override
  public VariableUpdateInstanceIndex getVariableUpdateInstanceIndex() {
    return new VariableUpdateInstanceIndexOS();
  }

  @Override
  public void deleteAllDocumentsInIndex(final String optimizeIndexAliasForIndex) {
    getOptimizeOpenSearchClient()
        .deleteByQuery(QueryDSL.matchAll(), true, optimizeIndexAliasForIndex);
  }

  @Override
  public void insertTestDocuments(
      final int amount, final String indexName, final String jsonDocument) throws IOException {
    getOptimizeOpenSearchClient()
        .getOpenSearchClient()
        .bulk(
            BulkRequest.of(
                r -> {
                  for (int i = 0; i < amount; i++) {
                    final int finalI = i;
                    r.operations(
                        o ->
                            o.index(
                                OptimizeIndexOperationOS.of(
                                    b -> {
                                      try {
                                        return b.optimizeIndex(
                                                getOptimizeOpenSearchClient(), indexName)
                                            .document(
                                                getObjectMapper()
                                                    .readValue(
                                                        String.format(jsonDocument, finalI),
                                                        Map.class));
                                      } catch (final JsonProcessingException e) {
                                        throw new RuntimeException(e);
                                      }
                                    })));
                  }
                  return r;
                }));
    getOptimizeOpenSearchClient().refresh(indexName);
  }

  @Override
  public void performLowLevelBulkRequest(
      final String methodName, final String endpoint, final String bulkPayload) throws IOException {
    final HttpEntity entity = new NStringEntity(bulkPayload, ContentType.APPLICATION_JSON);
    final Request request = new Request(methodName, endpoint);
    request.setEntity(entity);
    getOptimizeOpenSearchClient().getRestClient().performRequest(request);
  }

  @Override
  public void initSchema(final DatabaseSchemaManager schemaManager) {
    schemaManager.initializeSchema(getOptimizeOpenSearchClient());
  }

  @Override
  public Map<String, ? extends Object> getMappingFields(final String indexName) throws IOException {
    final GetMappingResponse getMappingResponse =
        getOptimizeOpenSearchClient().getMapping(new GetMappingRequest.Builder(), indexName);
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
      throw new OptimizeRuntimeException("Database index mapping properties should be of type map");
    }
  }

  @Override
  public boolean indexExists(final String indexOrAliasName, final Boolean addMappingFeatures) {
    return indexExists(indexOrAliasName);
  }

  @Override
  public boolean templateExists(final String optimizeIndexTemplateNameWithVersion)
      throws IOException {
    final ExistsTemplateRequest.Builder request =
        new ExistsTemplateRequest.Builder().name(optimizeIndexTemplateNameWithVersion);
    return getOptimizeOpenSearchClient()
        .getOpenSearchClient()
        .indices()
        .existsTemplate(request.build())
        .value();
  }

  @Override
  public boolean isAliasReadOnly(final String readOnlyAliasForIndex) throws IOException {
    final GetAliasResponse aliases =
        getOptimizeOpenSearchClient()
            .getAlias(
                GetAliasRequest.of(
                    a ->
                        a.name(
                            getOptimizeOpenSearchClient()
                                .applyIndexPrefixes(readOnlyAliasForIndex))));
    return aliases.result().values().stream()
        .flatMap(a -> a.aliases().values().stream())
        .collect(Collectors.toSet())
        .stream()
        .noneMatch(AliasDefinition::isWriteIndex);
  }

  @Override
  public List<String> getAllIndicesWithReadOnlyAlias(final String aliasNameWithPrefix) {
    final GetAliasResponse aliasResponse;
    try {
      aliasResponse = getOptimizeOpenSearchClient().getAlias(aliasNameWithPrefix);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
    final Map<String, IndexAliases> indexNameToAliasMap = aliasResponse.result();
    return indexNameToAliasMap.entrySet().stream()
        .filter(
            entry ->
                entry.getValue().aliases().values().stream()
                    .anyMatch(alias -> alias.isWriteIndex() != null && !alias.isWriteIndex()))
        .map(Map.Entry::getKey)
        .toList();
  }

  @Override
  public String[] getIndexNames() {
    return OpenSearchSchemaManager.getAllNonDynamicMappings().stream()
        .filter(IndexMappingCreator::isImportIndex)
        .map(getIndexNameService()::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new);
  }

  private IndexSettings createIndexSettings(
      final IndexMappingCreator indexMappingCreator,
      final ConfigurationService configurationService) {
    try {
      return OpenSearchIndexSettingsBuilder.buildAllSettings(
          configurationService, indexMappingCreator);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  public boolean indexExists(final String indexOrAliasName) {
    return getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .index()
        .indexExists(indexOrAliasName);
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

  private void createClientAndAddToCache(
      final String clientKey, final ConfigurationService configurationService) {
    final DatabaseConnectionNodeConfiguration osConfig =
        configurationService.getOpenSearchConfiguration().getFirstConnectionNode();
    LOG.info(
        "Creating OS Client with host {} and port {}", osConfig.getHost(), osConfig.getHttpPort());
    prefixAwareOptimizeOpenSearchClient =
        new OptimizeOpenSearchClient(
            OpenSearchClientBuilder.restClient(configurationService),
            OpenSearchClientBuilder.buildOpenSearchClientFromConfig(
                configurationService, new PluginRepository()),
            OpenSearchClientBuilder.buildOpenSearchAsyncClientFromConfig(
                configurationService, new PluginRepository()),
            new OptimizeIndexNameService(configurationService, DatabaseType.OPENSEARCH),
            new TransportOptionsProvider());
    adjustClusterSettings();
    CLIENT_CACHE.put(clientKey, prefixAwareOptimizeOpenSearchClient);
  }

  private ConfigurationService createConfigurationService() {
    final ConfigurationService configurationService =
        IntegrationTestConfigurationUtil.createItConfigurationService();
    if (customIndexPrefix != null) {
      configurationService
          .getOpenSearchConfiguration()
          .setIndexPrefix(
              configurationService.getOpenSearchConfiguration().getIndexPrefix()
                  + customIndexPrefix);
    }
    return configurationService;
  }

  private void adjustClusterSettings() {
    final PutClusterSettingsRequest.Builder settings =
        new PutClusterSettingsRequest.Builder()
            .persistent("action.auto_create_index", JsonData.of(true))
            .persistent("cluster.max_shards_per_node", JsonData.of(10000))
            .flatSettings(true);
    try {
      getOptimizeOpenSearchClient().getOpenSearchClient().cluster().putSettings(settings.build());
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not update cluster settings!", e);
    }
  }

  private <T> List<T> getAllDocumentsOfIndexAs(
      final String indexName, final Class<T> type, final Query query) {
    return getAllDocumentsOfIndicesAs(new String[] {indexName}, type, query);
  }

  private OptimizeIndexNameService getIndexNameService() {
    return getOptimizeOpenSearchClient().getIndexNameService();
  }

  private <T> List<T> getAllDocumentsOfIndicesAs(
      final String[] indexNames, final Class<T> type, final Query query) {
    final SearchRequest.Builder searchReqBuilder =
        RequestDSL.searchRequestBuilder()
            .index(List.of(indexNames))
            .query(query)
            .trackTotalHits(new TrackHits.Builder().enabled(true).build())
            .size(100);

    final String errorMessage = "Was not able to retrieve all documents for indices";
    final SearchResponse<T> searchResponse =
        getOptimizeOpenSearchClient().search(searchReqBuilder, type, errorMessage);
    return searchResponse.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
  }

  private void deleteIndexOfMapping(final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    deleteIndices(new String[] {indexMapping.getIndexName()});
  }

  private void deleteIndices(final String[] indicesToDelete) {
    getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .index()
        .deleteIndicesWithRetries(indicesToDelete);
  }

  private void updateZeebeRecordsByQuery(
      final String zeebeRecordPrefix,
      final String indexName,
      final Query query,
      final String updateScript) {
    updateRecordsByQuery(zeebeRecordPrefix + "_" + indexName + "*", query, updateScript);
  }

  private void updateRecordsByQuery(
      final String indexName, final Query query, final String updateScript) {
    getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .doc()
        .updateByQuery(
            indexName,
            query,
            OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
                updateScript, Collections.emptyMap()));
  }
}
