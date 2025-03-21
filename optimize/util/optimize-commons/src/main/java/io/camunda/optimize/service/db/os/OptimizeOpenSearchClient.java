/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static io.camunda.optimize.service.db.DatabaseConstants.GB_UNIT;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.os.client.dsl.RequestDSL.getRequestBuilder;
import static io.camunda.optimize.service.db.os.client.dsl.RequestDSL.scrollRequest;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.exceptions.ExceptionHelper.safe;
import static io.camunda.optimize.service.exceptions.ExceptionHelper.safeOS;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.DataImportSourceType;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.rest.exceptions.NotSupportedException;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.schema.TransportOptionsProvider;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.os.OpenSearchClientBuilder;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;
import org.opensearch.client.opensearch.core.UpdateByQueryResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkOperationBase;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.mget.MultiGetOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest.Builder;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.GetMappingRequest;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.opensearch.client.opensearch.indices.RolloverRequest;
import org.opensearch.client.opensearch.indices.RolloverResponse;
import org.opensearch.client.opensearch.indices.rollover.RolloverConditions;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.GetSnapshotResponse;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.ListRequest;
import org.opensearch.client.opensearch.tasks.ListResponse;
import org.opensearch.client.opensearch.tasks.Status;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;

public class OptimizeOpenSearchClient extends DatabaseClient {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OptimizeOpenSearchClient.class);

  private ExtendedOpenSearchClient openSearchClient;
  private OpenSearchAsyncClient openSearchAsyncClient;
  private RichOpenSearchClient richOpenSearchClient;
  private RestClient restClient;
  private TransportOptionsProvider transportOptionsProvider;
  private IndexNameServiceOS indexNameServiceOS;

  public OptimizeOpenSearchClient(
      final ExtendedOpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final OptimizeIndexNameService indexNameService) {
    this(openSearchClient, openSearchAsyncClient, indexNameService, new TransportOptionsProvider());
  }

  public OptimizeOpenSearchClient(
      final RestClient restClient,
      final ExtendedOpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final OptimizeIndexNameService indexNameService,
      final TransportOptionsProvider transportOptionsProvider) {
    this.openSearchClient = openSearchClient;
    this.indexNameService = indexNameService;
    this.transportOptionsProvider = transportOptionsProvider;
    this.openSearchAsyncClient = openSearchAsyncClient;
    richOpenSearchClient =
        new RichOpenSearchClient(openSearchClient, openSearchAsyncClient, indexNameService);
    this.restClient = restClient;
    indexNameServiceOS = new IndexNameServiceOS(indexNameService);
  }

  public OptimizeOpenSearchClient(
      final ExtendedOpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final OptimizeIndexNameService indexNameService,
      final TransportOptionsProvider transportOptionsProvider) {
    this.openSearchClient = openSearchClient;
    this.indexNameService = indexNameService;
    this.transportOptionsProvider = transportOptionsProvider;
    this.openSearchAsyncClient = openSearchAsyncClient;
    richOpenSearchClient =
        new RichOpenSearchClient(openSearchClient, openSearchAsyncClient, indexNameService);
    indexNameServiceOS = new IndexNameServiceOS(indexNameService);
  }

  public RestClient getRestClient() {
    if (restClient != null) {
      return restClient;
    } else {
      // We are creating this client only for testing, as there is currently no use in the normal
      // codebase. In case that becomes necessary this is a bit complicated because the AwsTransport
      // requires Apache 5, however the RestClient works with Apache 4, so we would need to
      // duplicate the entire logic for building the transport
      throw new NotSupportedException("RestClient is only available for testing");
    }
  }

  private static String getHintForErrorMsg(final boolean containsNestedDocumentLimitErrorMessage) {
    if (containsNestedDocumentLimitErrorMessage) {
      // exception potentially related to nested object limit
      return "If you are experiencing failures due to too many nested documents, try carefully increasing the "
          + "configured nested object limit (opensearch.settings.index.nested_documents_limit) or enabling the skipping of "
          + "documents that have reached this limit during import (import.skipDataAfterNestedDocLimitReached). "
          + "See Optimize documentation for details.";
    }
    return "";
  }

  private static BulkOperationBase typeByBulkOperation(final BulkOperation bulkOperation) {
    if (bulkOperation.isCreate()) {
      return bulkOperation.create();
    } else if (bulkOperation.isIndex()) {
      return bulkOperation.index();
    } else if (bulkOperation.isDelete()) {
      return bulkOperation.delete();
    } else if (bulkOperation.isUpdate()) {
      return bulkOperation.update();
    }
    throw new OptimizeRuntimeException(
        String.format(
            "The bulk operation with kind [%s] is not a supported operation.",
            bulkOperation._kind()));
  }

  private static Optional<Integer> taskProgress(final GetTasksResponse taskResponse) {
    try {
      final Status status = taskResponse.task().status();
      final int progress =
          (int)
              Math.round(
                  (status.updated() + status.deleted() + status.created() + status.noops())
                      * 100.0
                      / status.total());
      return Optional.of(progress);
    } catch (final Exception e) {
      LOG.error(format("Failed to compute task (ID:%s) progress!", taskResponse.task().id()), e);
      return Optional.empty();
    }
  }

  public static void validateTaskResponse(final GetTasksResponse taskResponse) {
    if (taskResponse.error() != null) {
      LOG.error("An Opensearch task failed with error: {}", taskResponse.error());
      throw new OptimizeRuntimeException(taskResponse.error().toString());
    }

    if (taskResponse.response() != null) {
      final List<String> failures = taskResponse.response().failures();
      if (failures != null && !failures.isEmpty()) {
        LOG.error("Opensearch task contains failures: {}", failures);
        throw new OptimizeRuntimeException(failures.toString());
      }
    }
  }

  public void createIndex(final CreateIndexRequest request) throws IOException {
    getOpenSearchClient().indices().create(request);
  }

  public final void close() {
    Optional.of(openSearchClient).ifPresent(OpenSearchClient::shutdown);
    Optional.of(openSearchAsyncClient).ifPresent(OpenSearchAsyncClient::shutdown);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    close();
    final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
    openSearchClient =
        OpenSearchClientBuilder.buildOpenSearchClientFromConfig(
            configurationService, new PluginRepository());
    openSearchAsyncClient =
        OpenSearchClientBuilder.buildOpenSearchAsyncClientFromConfig(
            configurationService, new PluginRepository());
    richOpenSearchClient =
        new RichOpenSearchClient(openSearchClient, openSearchAsyncClient, indexNameService);
    indexNameService = context.getBean(OptimizeIndexNameService.class);
    restClient = OpenSearchClientBuilder.restClient(configurationService);
    transportOptionsProvider = new TransportOptionsProvider(configurationService);
    indexNameServiceOS = new IndexNameServiceOS(indexNameService);
  }

  public final <T> GetResponse<T> get(
      final GetRequest.Builder requestBuilder,
      final Class<T> responseClass,
      final String errorMessage) {
    return richOpenSearchClient.doc().get(requestBuilder, responseClass, e -> errorMessage);
  }

  public final <T> GetResponse<T> get(
      final String index,
      final String id,
      final Class<T> responseClass,
      final String errorMessage) {
    final GetRequest.Builder requestBuilder = getRequestBuilder(index).id(id);
    return get(requestBuilder, responseClass, errorMessage);
  }

  public DeleteResponse delete(
      final DeleteRequest.Builder requestBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    return richOpenSearchClient.doc().delete(requestBuilder, errorMessageSupplier);
  }

  public DeleteResponse delete(
      final DeleteRequest.Builder requestBuilder, final String errorMessage) {
    return delete(requestBuilder, e -> errorMessage);
  }

  public DeleteResponse delete(final String indexName, final String entityId) {
    return richOpenSearchClient.doc().delete(indexName, entityId);
  }

  public final GetMappingResponse getMapping(
      final GetMappingRequest.Builder getMappingsRequest, final String... indexes)
      throws IOException {
    getMappingsRequest.index(Arrays.stream(convertToPrefixedAliasNames(indexes)).toList());
    return getOpenSearchClient().indices().getMapping(getMappingsRequest.build());
  }

  public void deleteIndexTemplateByIndexTemplateName(final String indexTemplateName) {
    final String prefixedIndexTemplateName =
        indexNameService.getOptimizeIndexAliasForIndex(indexTemplateName);
    LOG.debug("Deleting index template [{}].", prefixedIndexTemplateName);
    try {
      getOpenSearchClient().indices().deleteTemplate(b -> b.name(prefixedIndexTemplateName));
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Could not delete index template [%s]!", prefixedIndexTemplateName);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    LOG.debug("Successfully deleted index template [{}].", prefixedIndexTemplateName);
  }

  public <A, B> UpdateResponse<A> upsert(
      final UpdateRequest.Builder<A, B> requestBuilder,
      final Class<A> clazz,
      final Function<Exception, String> errorMessageSupplier) {
    return richOpenSearchClient.doc().upsert(requestBuilder, clazz, errorMessageSupplier);
  }

  public <T> UpdateResponse<Void> update(
      final UpdateRequest.Builder<Void, T> requestBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    return richOpenSearchClient.doc().update(requestBuilder, errorMessageSupplier);
  }

  public <T> UpdateResponse<Void> update(
      final UpdateRequest<Void, T> request, final Class<Void> clazz) throws IOException {
    return openSearchClient.update(request, clazz);
  }

  public <T> UpdateResponse<Void> update(
      final UpdateRequest.Builder<Void, T> requestBuilder, final String errorMessage) {
    return update(requestBuilder, e -> errorMessage);
  }

  public long deleteByQuery(final Query query, final boolean refresh, final String... index) {
    return richOpenSearchClient.doc().deleteByQuery(query, refresh, index);
  }

  public long updateByQuery(final String index, final Query query, final Script script) {
    return richOpenSearchClient.doc().updateByQuery(index, query, script);
  }

  public final <T> IndexResponse index(final IndexRequest.Builder<T> indexRequest) {
    return richOpenSearchClient.doc().index(indexRequest);
  }

  @Override
  public Map<String, Set<String>> getAliasesForIndexPattern(final String indexNamePattern)
      throws IOException {
    final GetAliasResponse aliases = getAlias(indexNamePattern);
    return aliases.result().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().aliases().keySet()));
  }

  @Override
  public Set<String> getAllIndicesForAlias(final String aliasName) {
    final GetAliasRequest aliasesRequest = new GetAliasRequest.Builder().name(aliasName).build();
    try {
      return openSearchClient.indices().getAlias(aliasesRequest).result().keySet();
    } catch (final OpenSearchException e) {
      if (e.response().status() == NOT_FOUND.code()) {
        return Set.of();
      }
      throw e;
    } catch (final Exception e) {
      final String message =
          String.format("Could not retrieve index names for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public final boolean exists(final String indexName) throws IOException {
    return exists(ExistsRequest.of(b -> b.index(List.of(convertToPrefixedAliasName(indexName)))));
  }

  @Override
  public boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB) {
    final RolloverRequest rolloverRequest =
        new RolloverRequest.Builder()
            .alias(convertToPrefixedAliasName(indexAliasName))
            .conditions(new RolloverConditions.Builder().maxSize(maxIndexSizeGB + GB_UNIT).build())
            .build();

    LOG.info("Executing rollover request on {}", indexAliasName);
    try {
      final RolloverResponse rolloverResponse = rollover(rolloverRequest);
      if (rolloverResponse.rolledOver()) {
        LOG.info(
            "Index with alias {} has been rolled over. New index name: {}",
            indexAliasName,
            rolloverResponse.newIndex());
      } else {
        LOG.debug(
            "Index with alias {} has not been rolled over. {}",
            indexAliasName,
            rolloverConditionsStatus(rolloverResponse.conditions()));
      }
      return rolloverResponse.rolledOver();
    } catch (final Exception e) {
      final String message = "Failed to execute rollover request";
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void deleteIndex(final String indexAlias) {
    if (getRichOpenSearchClient().index().deleteIndicesWithRetries(indexAlias)) {
      LOG.debug("Successfully deleted index with alias {}", indexAlias);
    } else {
      LOG.warn("Could not delete index with alias {}", indexAlias);
    }
  }

  @Override
  public long countWithoutPrefix(final String unprefixedIndex) {
    final CountRequest.Builder builder = new CountRequest.Builder().index(unprefixedIndex);

    try {
      return getOpenSearchClient().count(builder.build()).count();
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          String.format("Could not determine count from index: %s", unprefixedIndex));
    }
  }

  @Override
  public void refresh(final String indexPattern) {
    getRichOpenSearchClient().index().refresh(indexPattern);
  }

  @Override
  public List<String> getAllIndexNames() throws IOException {
    return new ArrayList<>(getRichOpenSearchClient().index().getIndexNamesWithRetries("*"));
  }

  @Override
  public List<String> addPrefixesToIndices(final String... indexes) {
    return List.of();
  }

  @Override
  public String getDatabaseVersion() throws IOException {
    return getOpenSearchClient().info().version().number();
  }

  @Override
  public void setDefaultRequestOptions() {
    // Do nothing, CustomerHeaderSupplier not supported for OpenSearch (see #10086)
  }

  @Override
  public void update(final String indexName, final String entityId, final ScriptData script) {

    final Script scr = QueryDSL.script(script.scriptString(), script.params());

    final UpdateRequest.Builder<Void, Void> updateReqBuilder =
        new UpdateRequest.Builder<Void, Void>()
            .id(entityId)
            .index(indexName)
            .script(scr)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    update(
        updateReqBuilder,
        String.format(
            "The error occurs while updating OpenSearch entity %s with id %s",
            indexName, entityId));
  }

  @Override
  public void executeImportRequestsAsBulk(
      final String bulkRequestName,
      final List<ImportRequestDto> importRequestDtos,
      final Boolean retryRequestIfNestedDocLimitReached) {

    if (importRequestDtos.isEmpty()) {
      LOG.warn("Cannot perform bulk request with empty collection of {}.", bulkRequestName);
    } else {
      final List<BulkOperation> operations =
          importRequestDtos.stream().map(this::createBulkOperation).toList();
      doBulkRequest(
          BulkRequest.Builder::new,
          operations,
          bulkRequestName,
          retryRequestIfNestedDocLimitReached);
    }
  }

  @Override
  public Set<String> performSearchDefinitionQuery(
      final String indexName,
      final String definitionXml,
      final String definitionIdField,
      final int maxPageSize,
      final String engineAlias) {
    LOG.debug("Performing " + indexName + " search query!");
    final Set<String> result = new HashSet<>();

    final BoolQuery filterQuery = buildBasicSearchDefinitionQuery(definitionXml, engineAlias);

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .field(definitionIdField)
                            .order(SortOrder.Desc)
                            .build())
                    .build())
            .index(indexName)
            .source(new SourceConfig.Builder().fetch(false).build())
            .query(filterQuery.toQuery());

    // refresh to ensure we see the latest state
    richOpenSearchClient.index().refresh(indexName);

    final String errorMessage = "Was not able to search for " + indexName + "!";

    final SearchResponse<DefinitionOptimizeResponseDto> searchResponse =
        search(searchRequest, DefinitionOptimizeResponseDto.class, errorMessage);

    LOG.debug(indexName + " search query got [{}] results", searchResponse.hits().hits());

    for (final Hit<DefinitionOptimizeResponseDto> hit : searchResponse.hits().hits()) {
      result.add(hit.id());
    }
    return result;
  }

  @Override
  public DatabaseType getDatabaseVendor() {
    return DatabaseType.OPENSEARCH;
  }

  @Override
  public void deleteIndexByRawIndexNames(final String... indexNames) {
    final String indexNamesString = Arrays.toString(indexNames);
    LOG.debug("Deleting indices [{}].", indexNamesString);
    dbClientSnapshotFailsafe("DeleteIndex: " + indexNamesString)
        .get(
            () ->
                getOpenSearchClient()
                    .indices()
                    .delete(DeleteIndexRequest.of(b -> b.index(List.of(indexNames)))));
    LOG.debug("Successfully deleted index [{}].", indexNamesString);
  }

  @Override
  public void deleteAllIndexes() {
    LOG.debug("Deleting all indexes.");
    try {
      final DeleteIndexResponse response =
          openSearchClient.indices().delete(new Builder().index("*").build());
      if (response.acknowledged()) {
        LOG.debug("Successfully deleted all indexes.");
      } else {
        LOG.warn("There was an error deleting all indexes.");
      }
    } catch (final IOException e) {
      LOG.warn("There was an error deleting all indexes.", e);
    }
  }

  public long count(final String[] indexNames, final Query query) throws IOException {
    return count(
        indexNames, query, "Could not execute count request for " + Arrays.toString(indexNames));
  }

  private boolean exists(final ExistsRequest existsRequest) throws IOException {
    return openSearchClient.indices().exists(existsRequest).value();
  }

  public <R> ScrollResponse<R> scroll(final ScrollRequest scrollRequest, final Class<R> entityClass)
      throws IOException {
    return richOpenSearchClient.doc().scroll(scrollRequest, entityClass);
  }

  public <R> Map<String, Aggregate> scrollWith(
      final SearchResponse<R> response,
      final Consumer<List<Hit<R>>> hitsConsumer,
      final Class<R> clazz,
      final int limit) {
    return safe(
        () ->
            richOpenSearchClient.doc().scrollWith(null, response, hitsConsumer, null, clazz, limit),
        e -> format("Could not scroll through entries for class [%s].", clazz.getSimpleName()),
        LOG);
  }

  public <T> MgetResponse<T> mget(
      final Class<T> responseType,
      final String errorMessage,
      final List<MultiGetOperation> operations) {
    return richOpenSearchClient.doc().mget(responseType, e -> errorMessage, operations);
  }

  public final GetAliasResponse getAlias(final String indexNamePattern) throws IOException {
    final GetAliasRequest getAliasesRequest =
        new GetAliasRequest.Builder().index(convertToPrefixedAliasName(indexNamePattern)).build();
    return getAlias(getAliasesRequest);
  }

  public final GetAliasResponse getAlias(final GetAliasRequest getAliasesRequest)
      throws IOException {
    return openSearchClient.indices().getAlias(getAliasesRequest);
  }

  public long count(final String[] indexNames, final Query query, final String errorMessage) {
    final CountRequest.Builder countReqBuilder =
        new CountRequest.Builder().index(List.of(indexNames)).query(query);
    return richOpenSearchClient.doc().count(countReqBuilder, e -> errorMessage).count();
  }

  public long count(final String indexName, final String errorMessage) {
    return count(new String[] {indexName}, QueryDSL.matchAll(), errorMessage);
  }

  public UpdateByQueryResponse submitUpdateTask(final UpdateByQueryRequest request)
      throws IOException {
    return getOpenSearchClient().updateByQuery(request);
  }

  public DeleteByQueryResponse submitDeleteTask(final DeleteByQueryRequest request)
      throws IOException {
    return getOpenSearchClient().deleteByQuery(request);
  }

  public ReindexResponse submitReindexTask(final ReindexRequest request) throws IOException {
    return getOpenSearchClient().reindex(request);
  }

  public ListResponse getTaskList(final ListRequest request) throws IOException {
    return getOpenSearchClient().tasks().list(request);
  }

  public <T> OpenSearchDocumentOperations.AggregatedResult<Hit<T>> retrieveAllScrollResults(
      final SearchRequest.Builder requestBuilder, final Class<T> responseType) throws IOException {
    return richOpenSearchClient.doc().scrollHits(requestBuilder, responseType);
  }

  public <R> List<R> scrollValues(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass) {
    return richOpenSearchClient.doc().scrollValues(requestBuilder, entityClass);
  }

  public <R> ScrollResponse<R> scroll(
      final String scrollId, final String timeout, final Class<R> entityClass) throws IOException {
    return richOpenSearchClient.doc().scroll(scrollRequest(scrollId, timeout), entityClass);
  }

  public <T> MgetResponse<T> mget(
      final Class<T> responseType,
      final String errorMessage,
      final Map<String, String> indexesToEntitiesId) {
    return richOpenSearchClient.doc().mget(responseType, e -> errorMessage, indexesToEntitiesId);
  }

  public <T> SearchResponse<T> search(
      final SearchRequest.Builder requestBuilder,
      final Class<T> responseType,
      final String errorMessage) {
    return richOpenSearchClient.doc().search(requestBuilder, responseType, e -> errorMessage);
  }

  public <T> SearchResponse<T> searchWithFixedAggregations(
      final SearchRequest.Builder requestBuilder,
      final Class<T> responseType,
      final String errorMessage) {
    final SearchRequest searchRequest = indexNameServiceOS.applyIndexPrefix(requestBuilder).build();
    return safeOS(
        () -> openSearchClient.searchWithFixedAggregations(searchRequest, responseType),
        e -> errorMessage,
        LOG);
  }

  public <T> SearchResponse<T> searchUnsafe(
      final SearchRequest request, final Class<T> responseType) throws IOException {
    return richOpenSearchClient.doc().unsafeSearch(request, responseType);
  }

  public <R> List<R> searchValues(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass) {
    return richOpenSearchClient.doc().searchValues(requestBuilder, entityClass);
  }

  public void clearScroll(
      final String scrollId, final Function<Exception, String> errorMessageSupplier) {
    safe(
        () -> {
          richOpenSearchClient.doc().clearScroll(scrollId);
          return null;
        },
        errorMessageSupplier,
        LOG);
  }

  private BulkOperation createBulkOperation(final ImportRequestDto requestDto) {
    validateOperationParams(requestDto);

    final String indexWithPrefix = richOpenSearchClient.getIndexAliasFor(requestDto.getIndexName());
    switch (requestDto.getType()) {
      case INDEX -> {
        return new BulkOperation.Builder()
            .index(
                new IndexOperation.Builder<>()
                    .id(requestDto.getId())
                    .document(requestDto.getSource())
                    .index(indexWithPrefix)
                    .build())
            .build();
      }
      case UPDATE -> {
        return new BulkOperation.Builder()
            .update(
                new UpdateOperation.Builder<>()
                    .id(requestDto.getId())
                    .index(indexWithPrefix)
                    .upsert(requestDto.getSource())
                    .script(
                        QueryDSL.scriptFromJsonData(
                            requestDto.getScriptData().scriptString(),
                            requestDto.getScriptData().params().entrySet().stream()
                                .collect(
                                    Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> JsonData.of(entry.getValue())))))
                    .retryOnConflict(requestDto.getRetryNumberOnConflict())
                    .build())
            .build();
      }
      default ->
          throw new OptimizeRuntimeException("The type of bulk operation is not specified for OS");
    }
  }

  public <T> void doImportBulkRequestWithList(
      final String importItemName,
      final List<T> entityCollection,
      final Function<T, BulkOperation> addDtoToRequestConsumer,
      final Boolean retryRequestIfNestedDocLimitReached,
      final String indexName) {
    if (entityCollection.isEmpty()) {
      LOG.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final List<BulkOperation> operations =
          entityCollection.stream().map(addDtoToRequestConsumer).toList();
      doBulkRequest(
          () -> new BulkRequest.Builder().index(indexName),
          operations,
          importItemName,
          retryRequestIfNestedDocLimitReached);
    }
  }

  public <T> void doImportBulkRequestWithList(
      final String importItemName,
      final List<T> entityCollection,
      final Function<T, BulkOperation> addDtoToRequestConsumer,
      final Boolean retryRequestIfNestedDocLimitReached) {
    if (entityCollection.isEmpty()) {
      LOG.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final List<BulkOperation> operations =
          entityCollection.stream().map(addDtoToRequestConsumer).toList();
      doBulkRequest(
          BulkRequest.Builder::new,
          operations,
          importItemName,
          retryRequestIfNestedDocLimitReached);
    }
  }

  public void doBulkRequest(
      final Supplier<BulkRequest.Builder> bulkReqBuilderSupplier,
      final List<BulkOperation> operations,
      final String itemName,
      final boolean retryRequestIfNestedDocLimitReached) {
    if (retryRequestIfNestedDocLimitReached) {
      doBulkRequestWithNestedDocHandling(bulkReqBuilderSupplier, operations, itemName);
    } else {
      doBulkRequestWithoutRetries(bulkReqBuilderSupplier.get(), operations, itemName);
    }
  }

  private void doBulkRequestWithNestedDocHandling(
      final Supplier<BulkRequest.Builder> bulkReqBuilderSupplier,
      final List<BulkOperation> operations,
      final String itemName) {
    if (operations.isEmpty()) {
      LOG.debug("Bulk request on {} not executed because it contains no actions.", itemName);
      return;
    }

    LOG.info("Executing bulk request on {} items of {}", operations.size(), itemName);
    final String errorMessage =
        String.format("There were errors while performing a bulk on %s.", itemName);
    final BulkResponse bulkResponse =
        bulk(bulkReqBuilderSupplier.get().operations(operations), errorMessage);
    if (bulkResponse.errors()) {
      final Set<String> failedNestedDocLimitItemIds =
          bulkResponse.items().stream()
              .filter(operation -> Objects.nonNull(operation.error()))
              .filter(operation -> operation.error().reason().contains(NESTED_DOC_LIMIT_MESSAGE))
              .map(BulkResponseItem::id)
              .collect(Collectors.toSet());
      if (!failedNestedDocLimitItemIds.isEmpty()) {
        LOG.warn(
            "There were failures while performing bulk on {} due to the nested document limit being reached."
                + " Removing {} failed items and retrying",
            itemName,
            failedNestedDocLimitItemIds.size());
        final List<BulkOperation> nonFailedOperations =
            operations.stream()
                .filter(
                    request ->
                        !failedNestedDocLimitItemIds.contains(typeByBulkOperation(request).id()))
                .toList();
        if (!nonFailedOperations.isEmpty()) {
          doBulkRequestWithNestedDocHandling(bulkReqBuilderSupplier, nonFailedOperations, itemName);
        }
      } else {
        throw new OptimizeRuntimeException(
            String.format("There were failures while performing bulk on %s.", itemName));
      }
    }
  }

  private void doBulkRequestWithoutRetries(
      final BulkRequest.Builder bulkReqBuilder,
      final List<BulkOperation> operations,
      final String itemName) {
    if (!operations.isEmpty()) {
      final String errorMessage =
          String.format("There were errors while performing a bulk on %s.", itemName);
      final BulkResponse bulkResponse = bulk(bulkReqBuilder.operations(operations), errorMessage);
      if (bulkResponse.errors()) {
        final boolean isReachedNestedDocLimit =
            bulkResponse.items().stream()
                .map(BulkResponseItem::error)
                .filter(Objects::nonNull)
                .map(ErrorCause::reason)
                .filter(Objects::nonNull)
                .anyMatch(reason -> reason.contains(NESTED_DOC_LIMIT_MESSAGE));
        throw new OptimizeRuntimeException(
            String.format(
                "There were failures while performing bulk on %s.%n%s",
                itemName, getHintForErrorMsg(isReachedNestedDocLimit)));
      }
    } else {
      LOG.debug("Bulk request on {} not executed because it contains no actions.", itemName);
    }
  }

  public BulkResponse bulk(final BulkRequest.Builder bulkRequest, final String errorMessage) {
    return richOpenSearchClient.doc().bulk(bulkRequest, e -> errorMessage);
  }

  private BoolQuery buildBasicSearchDefinitionQuery(
      final String definitionXml, final String engineAlias) {
    return new BoolQuery.Builder()
        .mustNot(QueryDSL.exists(definitionXml))
        .must(QueryDSL.term(DEFINITION_DELETED, "false"))
        .must(
            QueryDSL.term(
                DATA_SOURCE + "." + DataSourceDto.Fields.type,
                DataImportSourceType.ENGINE.toString()))
        .must(QueryDSL.term(DATA_SOURCE + "." + DataSourceDto.Fields.name, engineAlias))
        .build();
  }

  public final RolloverResponse rollover(final RolloverRequest rolloverRequest) throws IOException {
    return openSearchClient.indices().rollover(rolloverRequest);
  }

  private RolloverRequest applyAliasPrefixAndRolloverConditions(final RolloverRequest request) {
    return new RolloverRequest.Builder()
        .alias(indexNameService.getOptimizeIndexAliasForIndex(request.alias()))
        .conditions(request.conditions())
        .build();
  }

  private String rolloverConditionsStatus(final Map<String, Boolean> conditions) {
    final String conditionsNotMet =
        conditions.entrySet().stream()
            .filter(entry -> !entry.getValue())
            .map(entry -> "Condition " + entry.getKey() + " not met")
            .collect(Collectors.joining(", "));

    if (conditionsNotMet.isEmpty()) {
      return "Rollover not accomplished although all rollover conditions have been met.";
    } else {
      return conditionsNotMet;
    }
  }

  public List<String> applyIndexPrefixes(final String... indices) {
    return Arrays.asList(convertToPrefixedAliasNames(indices));
  }

  public boolean updateByQueryTask(
      final String updateItemIdentifier,
      final Script updateScript,
      final Query filterQuery,
      final String... indices) {
    LOG.debug("Updating {}", updateItemIdentifier);
    final UpdateByQueryRequest.Builder requestBuilder =
        new UpdateByQueryRequest.Builder()
            .index(applyIndexPrefixes(indices))
            .query(filterQuery)
            .conflicts(Conflicts.Proceed)
            .script(updateScript)
            .refresh(true);
    final Function<Exception, String> errorMessage =
        e ->
            format(
                "Failed to create updateByQuery task for [%s] with query [%s]!",
                updateItemIdentifier, filterQuery);

    final String taskId =
        safe(
            () ->
                richOpenSearchClient
                    .async()
                    .doc()
                    .updateByQuery(requestBuilder, errorMessage)
                    .get()
                    .task(),
            errorMessage,
            LOG);

    waitUntilTaskIsFinished(taskId, updateItemIdentifier);

    final Status taskStatus = richOpenSearchClient.task().task(taskId).response();
    LOG.debug("Updated [{}] {}.", taskStatus.updated(), updateItemIdentifier);
    return taskStatus.updated() > 0L;
  }

  public boolean deleteByQueryTask(
      final String deleteItemIdentifier,
      final Query filterQuery,
      final boolean refresh,
      final String... indices) {
    LOG.debug("Deleting {}", deleteItemIdentifier);
    final DeleteByQueryRequest.Builder requestBuilder =
        new DeleteByQueryRequest.Builder()
            .index(applyIndexPrefixes(indices))
            .conflicts(Conflicts.Proceed)
            .query(filterQuery)
            .refresh(refresh);
    final Function<Exception, String> errorMessage =
        e ->
            format(
                "Failed to create deleteByQuery task for [%s] with query [%s]!",
                deleteItemIdentifier, filterQuery);

    final String taskId =
        safe(
            () ->
                richOpenSearchClient
                    .async()
                    .doc()
                    .deleteByQuery(requestBuilder, errorMessage)
                    .get()
                    .task(),
            errorMessage,
            LOG);

    waitUntilTaskIsFinished(taskId, deleteItemIdentifier);

    final Status taskStatus = richOpenSearchClient.task().task(taskId).response();
    LOG.debug("Deleted [{}] {}.", taskStatus.updated(), deleteItemIdentifier);
    return taskStatus.updated() > 0L;
  }

  public void waitUntilTaskIsFinished(final String taskId, final String taskItemIdentifier) {
    final BackoffCalculator backoffCalculator = new BackoffCalculator(1000, 10);
    boolean finished = false;
    int progress = -1;
    while (!finished) {
      try {
        final GetTasksResponse taskResponse = richOpenSearchClient.task().task(taskId);
        validateTaskResponse(taskResponse);

        final int currentProgress = taskProgress(taskResponse).orElse(-1);
        if (currentProgress != progress) {
          final Status taskStatus = taskResponse.task().status();
          progress = currentProgress;
          LOG.info(
              "Progress of task (ID:{}) on {}: {}% (total: {}, updated: {}, created: {}, deleted: {}). Completed: {}",
              taskId,
              taskItemIdentifier,
              progress,
              taskStatus.total(),
              taskStatus.updated(),
              taskStatus.created(),
              taskStatus.deleted(),
              taskResponse.completed());
        }
        if (taskResponse.completed()) {
          finished = true;
        } else {
          Thread.sleep(backoffCalculator.calculateSleepTime());
        }
      } catch (final InterruptedException e) {
        LOG.error("Waiting for Opensearch task (ID: {}) completion was interrupted!", taskId, e);
        Thread.currentThread().interrupt();
      } catch (final Exception e) {
        throw new OptimizeRuntimeException(
            format("Error while trying to read Opensearch task (ID: %s) progress!", taskId), e);
      }
    }
  }

  public CompletableFuture<CreateSnapshotResponse> triggerSnapshotAsync(
      final CreateSnapshotRequest createSnapshotRequest) {
    return safe(
        () -> openSearchAsyncClient.snapshot().create(createSnapshotRequest),
        e -> "Failed to triger snapshot creation!",
        LOG);
  }

  public ExtendedOpenSearchClient getOpenSearchClient() {
    return openSearchClient;
  }

  public RichOpenSearchClient getRichOpenSearchClient() {
    return richOpenSearchClient;
  }

  public void verifyRepositoryExists(final GetRepositoryRequest getRepositoriesRequest)
      throws IOException, OpenSearchException {
    openSearchClient.snapshot().getRepository(getRepositoriesRequest);
  }

  public GetSnapshotResponse getSnapshots(final GetSnapshotRequest getSnapshotRequest)
      throws IOException {
    return openSearchClient.getSnapshots(getSnapshotRequest);
  }
}
