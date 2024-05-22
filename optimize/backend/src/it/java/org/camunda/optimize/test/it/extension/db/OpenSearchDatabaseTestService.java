/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension.db;

import static org.camunda.optimize.ApplicationContextProvider.getBean;
import static org.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsWithPercentileInterpolation;

import com.google.common.collect.Iterables;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import jakarta.ws.rs.NotSupportedException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto.Fields;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.es.schema.index.events.EventProcessPublishStateIndexES;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.AggregationDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import org.camunda.optimize.service.db.os.schema.OpenSearchIndexSettingsBuilder;
import org.camunda.optimize.service.db.os.schema.OpenSearchMetadataService;
import org.camunda.optimize.service.db.os.schema.index.ExternalProcessVariableIndexOS;
import org.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import org.camunda.optimize.service.db.os.schema.index.TerminatedUserSessionIndexOS;
import org.camunda.optimize.service.db.os.schema.index.VariableUpdateInstanceIndexOS;
import org.camunda.optimize.service.db.os.schema.index.events.EventIndexOS;
import org.camunda.optimize.service.db.os.schema.index.events.EventSequenceCountIndexOS;
import org.camunda.optimize.service.db.os.schema.index.report.SingleProcessReportIndexOS;
import org.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.db.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.DatabaseType;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.test.repository.TestIndexRepositoryOS;
import org.camunda.optimize.upgrade.os.OpenSearchClientBuilder;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.core.TimeValue;
import org.jetbrains.annotations.NotNull;
import org.mockserver.integration.ClientAndServer;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOptions;
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
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TrackHits;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.testcontainers.shaded.org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class OpenSearchDatabaseTestService extends DatabaseTestService {

  private static final String MOCKSERVER_CLIENT_KEY = "MockServer";
  private static final Map<String, OptimizeOpenSearchClient> CLIENT_CACHE = new HashMap<>();
  private static final ClientAndServer mockServerClient = initMockServer();

  private String opensearchDatabaseVersion;

  private OptimizeOpenSearchClient prefixAwareOptimizeOpenSearchClient;

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
  public DatabaseClient getDatabaseClient() {
    return prefixAwareOptimizeOpenSearchClient;
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
    if (prefixAwareOptimizeOpenSearchClient == CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY)) {
      log.info("Resetting all MockServer expectations and logs");
      mockServerClient.reset();
      log.info("No longer using OS MockServer");
      initOsClient();
    }
  }

  @Override
  public ClientAndServer useDBMockServer() {
    log.info("Using OpenSearch MockServer");
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
  public Integer getCountOfCompletedInstances() {
    return getInstanceCountWithQuery(QueryDSL.exists(ProcessInstanceIndex.END_DATE));
  }

  @Override
  public Integer getCountOfCompletedInstancesWithIdsIn(final Set<Object> processInstanceIds) {
    final Set<String> stringProcessInstanceIds =
        processInstanceIds.stream().map(Object::toString).collect(Collectors.toSet());

    return getInstanceCountWithQuery(
        QueryDSL.and(
            QueryDSL.exists(ProcessInstanceIndex.END_DATE),
            QueryDSL.stringTerms(
                ProcessInstanceIndex.PROCESS_INSTANCE_ID, stringProcessInstanceIds)));
  }

  @Override
  public Integer getActivityCountForAllProcessInstances() {
    // TODO implement with #11121
    throw new NotImplementedException(
        "Not yet implemented for OpenSearch, will be implemented with issue #11121");
  }

  @Override
  public Integer getVariableInstanceCountForAllProcessInstances() {
    return getVariableInstanceCountForAllProcessInstances(QueryDSL.matchAll());
  }

  @Override
  public Integer getVariableInstanceCountForAllCompletedProcessInstances() {
    return getVariableInstanceCountForAllProcessInstances(
        QueryDSL.exists(ProcessInstanceIndex.END_DATE));
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

  @SneakyThrows
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
  public void deleteExternalEventSequenceCountIndex() {
    deleteIndexOfMapping(new EventSequenceCountIndexOS(EXTERNAL_EVENTS_INDEX_SUFFIX));
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
  public boolean indexExists(final String indexOrAliasName) {
    return getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .index()
        .indexExists(indexOrAliasName);
  }

  @Override
  @SneakyThrows
  public boolean zeebeIndexExists(final String indexName) {
    // Cannot use the rich client here because we need an unprefixed index name
    return getOptimizeOpenSearchClient()
        .getOpenSearchClient()
        .indices()
        .exists(new ExistsRequest.Builder().index(indexName).build())
        .value();
  }

  @SneakyThrows
  @Override
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(
      final String dbType, final String engine) {
    final Optional<TimestampBasedImportIndexDto> response =
        prefixAwareOptimizeOpenSearchClient
            .getRichOpenSearchClient()
            .doc()
            .getWithRetries(
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
  public void deleteAllExternalEventIndices() {
    final String eventIndexAlias =
        getIndexNameService().getOptimizeIndexAliasForIndex(new EventIndexOS());
    final String[] eventIndices =
        getOptimizeOpenSearchClient().getAllIndicesForAlias(eventIndexAlias).toArray(String[]::new);
    deleteIndices(eventIndices);
  }

  @SneakyThrows
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

  @SneakyThrows
  @Override
  public void deleteAllOtherZeebeRecordsWithPrefix(
      final String zeebeRecordPrefix, final String recordsToKeep) {
    // Since we are retrieving zeebe records, we cannot use the rich opensearch client,
    // because it will add optimize prefixes to the request
    final GetIndexResponse allIndices =
        getOptimizeOpenSearchClient()
            .getOpenSearchClient()
            .indices()
            .get(RequestDSL.getIndexRequestBuilder("*").ignoreUnavailable(true).build());

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

  @SneakyThrows
  @Override
  public void updateZeebeRecordsForPrefix(
      final String zeebeRecordPrefix, final String indexName, final String updateScript) {
    updateZeebeRecordsByQuery(zeebeRecordPrefix, indexName, QueryDSL.matchAll(), updateScript);
  }

  @SneakyThrows
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

  @SneakyThrows
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

  @SneakyThrows
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
  public Map<AggregationDto, Double> calculateExpectedValueGivenDurations(
      final Number... setDuration) {
    return calculateExpectedValueGivenDurationsWithPercentileInterpolation(setDuration);
  }

  @Override
  @SneakyThrows
  public long countRecordsByQuery(
      final TermsQueryContainer queryContainer, final String expectedIndex) {
    return getOptimizeOpenSearchClient()
        .count(new String[] {expectedIndex}, queryContainer.toOpenSearchQuery());
  }

  @Override
  @SneakyThrows
  public <T> List<T> getZeebeExportedRecordsByQuery(
      final String exportIndex,
      final TermsQueryContainer queryForZeebeRecords,
      final Class<T> zeebeRecordClass) {
    final BoolQuery query = queryForZeebeRecords.toOpenSearchQuery();
    final SearchRequest.Builder searchRequest =
        RequestDSL.searchRequestBuilder().index(exportIndex).query(query.toQuery()).size(100);
    return getOptimizeOpenSearchClient()
        .getOpenSearchClient()
        .search(searchRequest.build(), zeebeRecordClass)
        .hits()
        .hits()
        .stream()
        .map(Hit::source)
        .toList();
  }

  @Override
  public void updateEventProcessRoles(
      final String eventProcessId,
      final List<IdentityDto> identityDtos,
      final ScriptData scriptData) {
    getOptimizeOpenSearchClient()
        .update(EVENT_PROCESS_MAPPING_INDEX_NAME, eventProcessId, scriptData);
  }

  @Override
  public Map<String, List<AliasMetadata>> getEventProcessInstanceIndicesWithAliasesFromDatabase() {
    // TODO implement with #11121
    throw new NotImplementedException(
        "Not yet implemented for OpenSearch, will be implemented with issue #11121");
  }

  @Override
  @SneakyThrows
  public Optional<EventProcessPublishStateDto> getEventProcessPublishStateDtoFromDatabase(
      final String processMappingId) {
    final SearchRequest.Builder searchRequest =
        new Builder()
            .index(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
            .query(
                QueryDSL.and(
                    QueryDSL.term(
                        EventProcessPublishStateIndexES.PROCESS_MAPPING_ID, processMappingId),
                    QueryDSL.term(EventProcessPublishStateIndexES.DELETED, false)))
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .field(EventProcessPublishStateIndexES.PUBLISH_DATE_TIME)
                            .order(org.opensearch.client.opensearch._types.SortOrder.Desc)
                            .build())
                    .build());
    final SearchResponse<EsEventProcessPublishStateDto> searchResponse =
        getOptimizeOpenSearchClient()
            .getRichOpenSearchClient()
            .doc()
            .unsafeSearch(searchRequest.build(), EsEventProcessPublishStateDto.class);
    EventProcessPublishStateDto result = null;
    if (!searchResponse.hits().hits().isEmpty()) {
      result =
          Objects.requireNonNull(searchResponse.hits().hits().get(0).source())
              .toEventProcessPublishStateDto();
    }
    return Optional.ofNullable(result);
  }

  @Override
  public Optional<EventProcessDefinitionDto> getEventProcessDefinitionFromDatabase(
      final String definitionId) {
    return Optional.ofNullable(
        getOptimizeOpenSearchClient()
            .get(
                RequestDSL.getRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME, definitionId),
                EventProcessDefinitionDto.class,
                "Could not retrieve entry from index "
                    + EVENT_PROCESS_DEFINITION_INDEX_NAME
                    + " with id "
                    + definitionId)
            .source());
  }

  @Override
  public List<EventProcessInstanceDto> getEventProcessInstancesFromDatabaseForProcessPublishStateId(
      final String publishStateId) {
    return getAllDocumentsOfIndicesAs(
        new String[] {EventProcessInstanceIndex.constructIndexName(publishStateId)},
        EventProcessInstanceDto.class,
        QueryDSL.matchAll());
  }

  @Override
  public void deleteProcessInstancesFromIndex(final String indexName, final String id) {
    getOptimizeOpenSearchClient().getRichOpenSearchClient().doc().delete(indexName, id);
  }

  @Override
  public void deleteDatabaseEntryById(final String indexName, final String id) {
    getOptimizeOpenSearchClient().delete(indexName, id);
  }

  @Override
  public DatabaseType getDatabaseVendor() {
    return DatabaseType.OPENSEARCH;
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
  public int getNestedDocumentsLimit(final ConfigurationService configurationService) {
    return configurationService.getOpenSearchConfiguration().getNestedDocumentsLimit();
  }

  @Override
  public void setNestedDocumentsLimit(
      final ConfigurationService configurationService, final int nestedDocumentsLimit) {
    configurationService.getOpenSearchConfiguration().setNestedDocumentsLimit(nestedDocumentsLimit);
  }

  @Override
  @SneakyThrows
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
    osClient
        .getRichOpenSearchClient()
        .index()
        .putSettings(
            new PutIndicesSettingsRequest.Builder()
                .settings(OpenSearchIndexSettingsBuilder.buildDynamicSettings(configurationService))
                .index(indexName)
                .build());
  }

  @Override
  public Optional<MetadataDto> readMetadata() {
    return getBean(OpenSearchMetadataService.class).readMetadata(getOptimizeOpenSearchClient());
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

  @Override
  @SneakyThrows
  public String getDatabaseVersion() {
    if (opensearchDatabaseVersion == null) {
      opensearchDatabaseVersion =
          OpenSearchClientBuilder.getCurrentOSVersion(
              getOptimizeOpenSearchClient().getOpenSearchClient());
    }
    return opensearchDatabaseVersion;
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
  @SneakyThrows
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
  public void removeStoredOrderCountersForDefinitionKey(
      final String definitionKey, final ScriptData script) {
    getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .doc()
        .updateByQuery(
            CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + definitionKey,
            QueryDSL.matchAll(),
            QueryDSL.script(script.scriptString(), script.params()));
  }

  @Override
  @SneakyThrows
  public List<String> getAllIndicesWithWriteAlias(final String aliasNameWithPrefix) {
    final GetAliasResponse aliasResponse =
        getOptimizeOpenSearchClient().getAlias(aliasNameWithPrefix);
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
  @SneakyThrows
  public List<String> getAllIndicesWithReadOnlyAlias(final String aliasNameWithPrefix) {
    final GetAliasResponse aliasResponse =
        getOptimizeOpenSearchClient().getAlias(aliasNameWithPrefix);
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
  @SneakyThrows
  public void deleteTraceStateImportIndexForDefinitionKey(final String definitionKey) {
    getOptimizeOpenSearchClient()
        .getRichOpenSearchClient()
        .doc()
        .delete(
            TIMESTAMP_BASED_IMPORT_INDEX_NAME,
            Fields.esTypeIndexRefersTo,
            EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + definitionKey.toLowerCase(Locale.ENGLISH));
  }

  @Override
  public void verifyThatAllDocumentsOfIndexAreRelatedToRunningInstancesOnly(
      final String entityIndex,
      final String processInstanceField,
      final TimeValue scrollKeepAlive) {
    throw new NotSupportedException("This operation is not supported for OpenSearch yet");
  }

  @Override
  public Integer getVariableInstanceCount(final String variableName) {
    // TODO implement with #11121
    throw new NotImplementedException(
        "Not yet implemented for OpenSearch, will be implemented with issue #11121");
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
    log.info(
        "Creating OS Client with host {} and port {}", osConfig.getHost(), osConfig.getHttpPort());
    prefixAwareOptimizeOpenSearchClient =
        new OptimizeOpenSearchClient(
            OpenSearchClientBuilder.buildOpenSearchClientFromConfig(configurationService),
            OpenSearchClientBuilder.buildOpenSearchAsyncClientFromConfig(configurationService),
            new OptimizeIndexNameService(configurationService, DatabaseType.OPENSEARCH));
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

  @SneakyThrows
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

  private int getInstanceCountWithQuery(final Query query) {
    try {
      return Long.valueOf(
              getOptimizeOpenSearchClient()
                  .count(new String[] {PROCESS_INSTANCE_MULTI_ALIAS}, query))
          .intValue();
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(
          "Cannot evaluate document count for index " + PROCESS_INSTANCE_MULTI_ALIAS, e);
    }
  }

  private Integer getVariableInstanceCountForAllProcessInstances(final Query processInstanceQuery) {
    // TODO implement with #11121
    throw new NotImplementedException(
        "Not yet implemented for OpenSearch, will be implemented with issue #11121");
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
