/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.DatabaseType;
import org.camunda.optimize.upgrade.os.OpenSearchClientBuilder;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.CountResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkOperationBase;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.RolloverRequest;
import org.opensearch.client.opensearch.indices.RolloverResponse;
import org.opensearch.client.opensearch.indices.rollover.RolloverConditions;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Status;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.camunda.optimize.service.db.DatabaseConstants.GB_UNIT;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.getRequestBuilder;
import static org.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static org.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static org.camunda.optimize.service.exceptions.ExceptionHelper.safe;

@Slf4j
public class OptimizeOpenSearchClient extends DatabaseClient {

  private final static String NESTED_DOC_LIMIT_MESSAGE = "nested";

  @Getter
  private OpenSearchClient openSearchClient;

  @Getter
  private OpenSearchAsyncClient openSearchAsyncClient;

  private RequestOptionsProvider requestOptionsProvider;

  @Getter
  private RichOpenSearchClient richOpenSearchClient;

  public OptimizeOpenSearchClient(final OpenSearchClient openSearchClient,
                                  final OpenSearchAsyncClient openSearchAsyncClient,
                                  final OptimizeIndexNameService indexNameService) {
    this(openSearchClient, openSearchAsyncClient, indexNameService, new RequestOptionsProvider());
  }

  public OptimizeOpenSearchClient(final OpenSearchClient openSearchClient,
                                  final OpenSearchAsyncClient openSearchAsyncClient,
                                  final OptimizeIndexNameService indexNameService,
                                  final RequestOptionsProvider requestOptionsProvider) {
    this.openSearchClient = openSearchClient;
    this.indexNameService = indexNameService;
    this.requestOptionsProvider = requestOptionsProvider;
    this.openSearchAsyncClient = openSearchAsyncClient;
    this.richOpenSearchClient = new RichOpenSearchClient(openSearchClient, openSearchAsyncClient, indexNameService);
  }

  public final void close() {
    Optional.of(openSearchClient).ifPresent(OpenSearchClient::shutdown);
    Optional.of(openSearchAsyncClient).ifPresent(OpenSearchAsyncClient::shutdown);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    close();
    final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
    this.openSearchClient = OpenSearchClientBuilder.buildOpenSearchClientFromConfig(configurationService);
    this.openSearchAsyncClient = OpenSearchClientBuilder.buildOpenSearchAsyncClientFromConfig(configurationService);
    this.richOpenSearchClient =
      new RichOpenSearchClient(openSearchClient,
                               openSearchAsyncClient,
                               indexNameService);
    this.indexNameService = context.getBean(OptimizeIndexNameService.class);
    // For now we are descoping the custom header provider, to be evaluated with OPT-7400
    this.requestOptionsProvider = new RequestOptionsProvider(List.of(), configurationService);
  }

  public final <T> GetResponse<T> get(final GetRequest.Builder requestBuilder,
                                      final Class<T> responseClass,
                                      final String errorMessage) {
    return richOpenSearchClient.doc().get(requestBuilder, responseClass, e -> errorMessage);
  }

  public final <T> GetResponse<T> get(final String index,
                                      final String id,
                                      final Class<T> responseClass,
                                      final String errorMessage) {
    var requestBuilder = getRequestBuilder(index).id(id);
    return get(requestBuilder, responseClass, errorMessage);
  }

  public DeleteResponse delete(final DeleteRequest.Builder requestBuilder, Function<Exception, String> errorMessageSupplier) {
    return richOpenSearchClient.doc().delete(requestBuilder, errorMessageSupplier);
  }

  public DeleteResponse delete(final DeleteRequest.Builder requestBuilder, final String errorMessage) {
    return delete(requestBuilder, e -> errorMessage);
  }

  public DeleteResponse delete(final String indexName, final String entityId) {
    return richOpenSearchClient.doc().delete(indexName, entityId);
  }

  public <A, B> UpdateResponse<A> upsert(final UpdateRequest.Builder<A, B> requestBuilder, Class<A> clazz, Function<Exception,
    String> errorMessageSupplier) {
    return richOpenSearchClient.doc().upsert(requestBuilder, clazz, errorMessageSupplier);
  }

  public <T> UpdateResponse<Void> update(final UpdateRequest.Builder<Void, T> requestBuilder,
                                         Function<Exception, String> errorMessageSupplier) {
    return richOpenSearchClient.doc().update(requestBuilder, errorMessageSupplier);
  }

  public <T> UpdateResponse<Void> update(final UpdateRequest.Builder<Void, T> requestBuilder, final String errorMessage) {
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
  public Map<String, Set<String>> getAliasesForIndexPattern(final String indexNamePattern) throws IOException {
    final GetAliasResponse aliases = getAlias(indexNamePattern);
    return aliases.result().entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> entry.getValue().aliases().keySet()
      ));
  }

  @Override
  public Set<String> getAllIndicesForAlias(final String aliasName) {
    GetAliasRequest aliasesRequest = new GetAliasRequest.Builder().name(aliasName).build();
    try {
      return openSearchClient
        .indices()
        .getAlias(aliasesRequest)
        .result()
        .keySet();
    } catch (Exception e) {
      String message = String.format("Could not retrieve index names for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public final GetAliasResponse getAlias(String indexNamePattern) throws IOException {
    final GetAliasRequest getAliasesRequest =
      new GetAliasRequest.Builder()
        .index(convertToPrefixedAliasName(indexNamePattern))
        .build();
    return openSearchClient.indices().getAlias(getAliasesRequest);
  }

  @Override
  public boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB) {
    RolloverRequest rolloverRequest =
      new RolloverRequest.Builder()
        .alias(indexAliasName)
        .conditions(new RolloverConditions.Builder().maxSize(maxIndexSizeGB + GB_UNIT).build())
        .build();

    log.info("Executing rollover request on {}", indexAliasName);
    try {
      RolloverResponse rolloverResponse = this.rollover(rolloverRequest);
      if (rolloverResponse.rolledOver()) {
        log.info(
          "Index with alias {} has been rolled over. New index name: {}",
          indexAliasName,
          rolloverResponse.newIndex()
        );
      } else {
        log.debug("Index with alias {} has not been rolled over. {}", indexAliasName,
                  rolloverConditionsStatus(rolloverResponse.conditions())
        );
      }
      return rolloverResponse.rolledOver();
    } catch (Exception e) {
      String message = "Failed to execute rollover request";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void deleteIndex(final String indexAlias) {
    getRichOpenSearchClient().index().deleteIndicesWithRetries(indexAlias);
  }

  @Override
  public <T> long count(final String[] indexNames, final T query) throws IOException {
    return count(indexNames, query, "Could not execute count request for " + Arrays.toString(indexNames));
  }

  public <T> long count(final String[] indexNames, final T query, final String errorMessage) {
    if (query instanceof Query openSearchQuery) {
      final CountRequest.Builder countReqBuilder = new CountRequest.Builder()
        .index(List.of(indexNames))
        .query(openSearchQuery);
      final CountResponse response = richOpenSearchClient.doc().count(countReqBuilder, e -> errorMessage);
      return response.count();
    } else {
      throw new IllegalArgumentException("The count method requires an OpenSearch object of type Query, " +
                                           "instead got " + query.getClass().getSimpleName());
    }
  }

  public long count(final String indexName, final String errorMessage) {
    return count(new String[]{indexName}, QueryDSL.matchAll(), errorMessage);
  }

  //todo rename it in scope of OPT-7469
  public <T> OpenSearchDocumentOperations.AggregatedResult<Hit<T>> retrieveAllScrollResults(
    final SearchRequest.Builder requestBuilder, Class<T> responseType
  ) throws IOException {
    return richOpenSearchClient.doc().scrollHits(requestBuilder, responseType);
  }

  public <R> List<R> scrollValues(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return richOpenSearchClient.doc().scrollValues(requestBuilder, entityClass);
  }

  @Override
  public org.elasticsearch.action.search.SearchResponse scroll(final SearchScrollRequest scrollRequest) throws IOException {
    //todo will be handle in the OPT-7469
    return new org.elasticsearch.action.search.SearchResponse(null);
  }

  public <T> MgetResponse<T> mget(Class<T> responseType, final String errorMessage,
                                  Map<String, String> indexesToEntitiesId) {
    return richOpenSearchClient.doc().mget(responseType, e -> errorMessage, indexesToEntitiesId);
  }

  @Override
  public org.elasticsearch.action.search.SearchResponse search(
    final org.elasticsearch.action.search.SearchRequest searchRequest
  ) throws IOException {
    //todo will be handle in the OPT-7469
    return new org.elasticsearch.action.search.SearchResponse(null);
  }

  public <T> SearchResponse<T> search(final SearchRequest.Builder requestBuilder,
                                      final Class<T> responseType,
                                      final String errorMessage) {
    return richOpenSearchClient.doc().search(requestBuilder, responseType, e -> errorMessage);
  }

  public <R> List<R> searchValues(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return richOpenSearchClient.doc().searchValues(requestBuilder, entityClass);
  }

  @Override
  public ClearScrollResponse clearScroll(final ClearScrollRequest clearScrollRequest) throws IOException {
    //todo will be handle in the OPT-7469
    return new ClearScrollResponse(null);
  }

  @Override
  public String getDatabaseVersion() throws IOException {
    return getOpenSearchClient().info().version().number();
  }

  @Override
  public void setDefaultRequestOptions() {
    // TODO Do nothing, will be handled with OPT-7400
  }

  public void executeImportRequestsAsBulk(final String bulkRequestName,
                                          final List<ImportRequestDto> importRequestDtos,
                                          final Boolean retryRequestIfNestedDocLimitReached) {

    if (importRequestDtos.isEmpty()) {
      log.warn("Cannot perform bulk request with empty collection of {}.", bulkRequestName);
    } else {
      final BulkRequest.Builder bulkReqBuilder = new BulkRequest.Builder();
      List<BulkOperation> operations = importRequestDtos.stream()
        .map(this::createBulkOperation)
        .toList();
      doBulkRequest(bulkReqBuilder, operations, bulkRequestName, retryRequestIfNestedDocLimitReached);
    }
  }

  private BulkOperation createBulkOperation(final ImportRequestDto requestDto) {
    validateOperationParams(requestDto);

    final String indexWithPrefix = richOpenSearchClient.getIndexAliasFor(requestDto.getIndexName());
    switch (requestDto.getType()) {
      case INDEX -> {
        return new BulkOperation.Builder()
          .index(new IndexOperation.Builder<>()
                   .id(requestDto.getId())
                   .document(requestDto.getSource())
                   .index(indexWithPrefix)
                   .build())
          .build();
      }
      case UPDATE -> {
        return new BulkOperation.Builder()
          .update(new UpdateOperation.Builder<>()
                    .id(requestDto.getId())
                    .index(indexWithPrefix)
                    .upsert(requestDto.getSource())
                    .script(QueryDSL.scriptFromJsonData(
                      requestDto.getScriptData().scriptString(),
                      requestDto.getScriptData()
                        .params()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonData.of(entry.getValue())))
                    ))
                    .retryOnConflict(requestDto.getRetryNumberOnConflict())
                    .build())
          .build();
      }
      default -> throw new OptimizeRuntimeException("The type of bulk operation is not specified for OS");
    }
  }


  public <T> void doImportBulkRequestWithList(final String importItemName,
                                              final List<T> entityCollection,
                                              final Function<T, BulkOperation> addDtoToRequestConsumer,
                                              final Boolean retryRequestIfNestedDocLimitReached,
                                              final String indexName) {
    if (entityCollection.isEmpty()) {
      log.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final BulkRequest.Builder bulkReqBuilder = new BulkRequest.Builder()
        .index(indexName);
      List<BulkOperation> operations = entityCollection.stream().map(addDtoToRequestConsumer).toList();
      doBulkRequest(bulkReqBuilder, operations, importItemName, retryRequestIfNestedDocLimitReached);
    }
  }

  public <T> void doImportBulkRequestWithList(final String importItemName,
                                              final List<T> entityCollection,
                                              final Function<T, BulkOperation> addDtoToRequestConsumer,
                                              final Boolean retryRequestIfNestedDocLimitReached) {
    if (entityCollection.isEmpty()) {
      log.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final BulkRequest.Builder bulkReqBuilder = new BulkRequest.Builder();
      List<BulkOperation> operations = entityCollection.stream().map(addDtoToRequestConsumer).toList();
      doBulkRequest(bulkReqBuilder, operations, importItemName, retryRequestIfNestedDocLimitReached);
    }
  }

  public void doBulkRequest(final BulkRequest.Builder bulkReqBuilder,
                            final List<BulkOperation> operations,
                            final String itemName,
                            final boolean retryRequestIfNestedDocLimitReached) {
    if (retryRequestIfNestedDocLimitReached) {
      doBulkRequestWithNestedDocHandling(bulkReqBuilder, operations, itemName);
    } else {
      doBulkRequestWithoutRetries(bulkReqBuilder, operations, itemName);
    }
  }

  private void doBulkRequestWithNestedDocHandling(final BulkRequest.Builder bulkReqBuilder,
                                                  final List<BulkOperation> operations,
                                                  final String itemName) {
    if (!operations.isEmpty()) {
      log.info("Executing bulk request on {} items of {}", operations.size(), itemName);

      final String errorMessage = String.format("There were errors while performing a bulk on %s.", itemName);
      BulkResponse bulkResponse = this.bulk(bulkReqBuilder.operations(operations), errorMessage);
      if (bulkResponse.errors()) {
        final Set<String> failedNestedDocLimitItemIds = bulkResponse.items()
          .stream()
          .filter(operation -> Objects.nonNull(operation.error()))
          // TODO OPT-7352 we need to validate whether this is a valid way to check for nested document errors
          .filter(operation -> operation.error().reason().contains(NESTED_DOC_LIMIT_MESSAGE))
          .map(BulkResponseItem::id)
          .collect(Collectors.toSet());
        if (!failedNestedDocLimitItemIds.isEmpty()) {
          log.warn("There were failures while performing bulk on {} due to the nested document limit being reached." +
                     " Removing {} failed items and retrying", itemName, failedNestedDocLimitItemIds.size());
          operations.removeIf(request -> failedNestedDocLimitItemIds.contains(typeByBulkOperation(request).id()));
          if (!operations.isEmpty()) {
            doBulkRequestWithNestedDocHandling(bulkReqBuilder, operations, itemName);
          }
        } else {
          throw new OptimizeRuntimeException(String.format(
            "There were failures while performing bulk on %s.",
            itemName
          ));
        }
      }
    } else {
      log.debug("Bulk request on {} not executed because it contains no actions.", itemName);
    }
  }

  private static String getHintForErrorMsg(final boolean containsNestedDocumentLimitErrorMessage) {
    if (containsNestedDocumentLimitErrorMessage) {
      // exception potentially related to nested object limit
      return "If you are experiencing failures due to too many nested documents, try carefully increasing the " +
        "configured nested object limit or enabling the skipping of " +
        "documents that have reached this limit during import (import.skipDataAfterNestedDocLimitReached). " +
        "See Optimize documentation for details.";
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
    throw new OptimizeRuntimeException(String.format(
      "The bulk operation with kind [%s] is not a supported operation.",
      bulkOperation._kind()
    ));
  }

  private void doBulkRequestWithoutRetries(final BulkRequest.Builder bulkReqBuilder,
                                           final List<BulkOperation> operations,
                                           final String itemName) {
    if (!operations.isEmpty()) {
      final String errorMessage = String.format("There were errors while performing a bulk on %s.", itemName);
      BulkResponse bulkResponse = this.bulk(bulkReqBuilder.operations(operations), errorMessage);
      if (bulkResponse.errors()) {
        final boolean isReachedNestedDocLimit = bulkResponse.items()
          .stream()
          .map(BulkResponseItem::error)
          .filter(Objects::nonNull)
          .map(ErrorCause::reason)
          .filter(Objects::nonNull)
          .anyMatch(reason -> reason.contains(NESTED_DOC_LIMIT_MESSAGE));
        throw new OptimizeRuntimeException(String.format(
          "There were failures while performing bulk on %s.%n%s",
          itemName,
          getHintForErrorMsg(isReachedNestedDocLimit)
        ));
      }
    } else {
      log.debug("Bulk request on {} not executed because it contains no actions.", itemName);
    }
  }

  @Override
  public void update(final String indexName, final String entityId, final ScriptData script) {

    Script scr = QueryDSL.scriptFromJsonData(
      script.scriptString(),
      script.params().entrySet().stream().collect(Collectors.toMap(
        Map.Entry::getKey,
        JsonData::of
      ))
    );

    UpdateRequest.Builder<Void, Void> updateReqBuilder = new UpdateRequest.Builder<Void, Void>()
      .id(entityId)
      .index(indexName)
      .script(scr)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    this.update(
      updateReqBuilder,
      String.format("The error occurs while updating OpenSearch entity %s with id %s", indexName, entityId)
    );
  }

  @Override
  public Set<String> performSearchDefinitionQuery(final String indexName,
                                                  final String definitionXml,
                                                  final String definitionIdField,
                                                  final int maxPageSize,
                                                  final String engineAlias) {
    log.debug("Performing " + indexName + " search query!");
    Set<String> result = new HashSet<>();

    BoolQuery filterQuery = buildBasicSearchDefinitionQuery(definitionXml, engineAlias);

    SearchRequest.Builder searchRequest = new SearchRequest
      .Builder()
      .sort(new SortOptions.Builder()
              .field(new FieldSort.Builder()
                       .field(definitionIdField)
                       .order(SortOrder.Desc)
                       .build())
              .build())
      .index(indexName)
      .source(new SourceConfig.Builder().fetch(false).build())
      .query(filterQuery._toQuery());

    // refresh to ensure we see the latest state
    richOpenSearchClient.index().refresh(indexName);

    String errorMessage = "Was not able to search for " + indexName + "!";

    SearchResponse<DefinitionOptimizeResponseDto> searchResponse = this.search(
      searchRequest,
      DefinitionOptimizeResponseDto.class,
      errorMessage
    );

    log.debug(indexName + " search query got [{}] results", searchResponse.hits().hits());

    for (Hit<DefinitionOptimizeResponseDto> hit : searchResponse.hits().hits()) {
      result.add(hit.id());
    }
    return result;
  }

  public BulkResponse bulk(final BulkRequest.Builder bulkRequest, final String errorMessage) {
    return richOpenSearchClient.doc().bulk(bulkRequest, e -> errorMessage);
  }

  private BoolQuery buildBasicSearchDefinitionQuery(String definitionXml, String engineAlias) {
    return new BoolQuery.Builder()
      .mustNot(QueryDSL.exists(definitionXml))
      .must(QueryDSL.term(DEFINITION_DELETED, "false"))
      .must(QueryDSL.term(DATA_SOURCE + "." + DataSourceDto.Fields.type, DataImportSourceType.ENGINE.toString()))
      .must(QueryDSL.term(DATA_SOURCE + "." + DataSourceDto.Fields.name, engineAlias))
      .build();
  }

  public final RolloverResponse rollover(RolloverRequest rolloverRequest) throws IOException {
    rolloverRequest = applyAliasPrefixAndRolloverConditions(rolloverRequest);
    return openSearchClient.indices().rollover(rolloverRequest);
  }

  private RolloverRequest applyAliasPrefixAndRolloverConditions(final RolloverRequest request) {
    return new RolloverRequest.Builder()
      .alias(indexNameService.getOptimizeIndexAliasForIndex(request.alias()))
      .conditions(request.conditions())
      .build();
  }

  private String rolloverConditionsStatus(Map<String, Boolean> conditions) {
    String conditionsNotMet = conditions.entrySet().stream()
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

  public boolean updateByQueryTask(String updateItemIdentifier, Script updateScript, Query filterQuery, String... indices) {
    log.debug("Updating {}", updateItemIdentifier);
    UpdateByQueryRequest.Builder requestBuilder = new UpdateByQueryRequest.Builder()
      .index(applyIndexPrefixes(indices))
      .query(filterQuery)
      .conflicts(Conflicts.Proceed)
      .script(updateScript)
      .refresh(true);
    Function<Exception, String> errorMessage = e ->
      format("Failed to create updateByQuery task for [%s] with query [%s]!", updateItemIdentifier, filterQuery);

    final String taskId = safe(
      () -> richOpenSearchClient.async().doc().updateByQuery(requestBuilder, errorMessage).get().task(),
      errorMessage,
      log
    );

    waitUntilTaskIsFinished(taskId, updateItemIdentifier);

    final Status taskStatus = richOpenSearchClient.task().task(taskId).response();
    log.debug("Updated [{}] {}.", taskStatus.updated(), updateItemIdentifier);
    return taskStatus.updated() > 0L;
  }

  public boolean deleteByQueryTask(String deleteItemIdentifier, Query filterQuery, boolean refresh, String... indices) {
    log.debug("Deleting {}", deleteItemIdentifier);
    DeleteByQueryRequest.Builder requestBuilder = new DeleteByQueryRequest.Builder()
      .index(applyIndexPrefixes(indices))
      .conflicts(Conflicts.Proceed)
      .query(filterQuery)
      .refresh(refresh);
    Function<Exception, String> errorMessage = e ->
      format("Failed to create deleteByQuery task for [%s] with query [%s]!", deleteItemIdentifier, filterQuery);

    final String taskId = safe(
      () -> richOpenSearchClient.async().doc().deleteByQuery(requestBuilder, errorMessage).get().task(),
      errorMessage,
      log
    );

    waitUntilTaskIsFinished(taskId, deleteItemIdentifier);

    final Status taskStatus = richOpenSearchClient.task().task(taskId).response();
    log.debug("Deleted [{}] {}.", taskStatus.updated(), deleteItemIdentifier);
    return taskStatus.updated() > 0L;
  }

  @Override
  public DatabaseType getDatabaseVendor() {
    return DatabaseType.OPENSEARCH;
  }

  private static Optional<Integer> taskProgress(GetTasksResponse taskResponse) {
    try {
      Status status = taskResponse.task().status();
      int progress = (int) Math.round(
        (status.updated() + status.deleted() + status.created() + status.noops()) * 100.0 / status.total()
      );
      return Optional.of(progress);
    } catch (Exception e) {
      log.error(format("Failed to compute task (ID:%s) progress!", taskResponse.task().id()), e);
      return Optional.empty();
    }
  }

  private void waitUntilTaskIsFinished(final String taskId, final String taskItemIdentifier) {
    final BackoffCalculator backoffCalculator = new BackoffCalculator(1000, 10);
    boolean finished = false;
    int progress = -1;
    while (!finished) {
      try {
        final GetTasksResponse taskResponse = richOpenSearchClient.task().task(taskId);
        validateTaskResponse(taskResponse);

        int currentProgress = taskProgress(taskResponse).orElse(-1);
        if (currentProgress != progress) {
          final Status taskStatus = taskResponse.task().status();
          progress = currentProgress;
          log.info(
            "Progress of task (ID:{}) on {}: {}% (total: {}, updated: {}, created: {}, deleted: {}). Completed: {}",
            taskId,
            taskItemIdentifier,
            progress,
            taskStatus.total(),
            taskStatus.updated(),
            taskStatus.created(),
            taskStatus.deleted(),
            taskResponse.completed()
          );
        }
        if (taskResponse.completed()) {
          finished = true;
        } else {
          Thread.sleep(backoffCalculator.calculateSleepTime());
        }
      } catch (InterruptedException e) {
        log.error("Waiting for Opensearch task (ID: {}) completion was interrupted!", taskId, e);
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        throw new OptimizeRuntimeException(
          format("Error while trying to read Opensearch task (ID: %s) progress!", taskId), e
        );
      }
    }
  }

  private static void validateTaskResponse(final GetTasksResponse taskResponse) {
    if (taskResponse.error() != null) {
      log.error("An Opensearch task failed with error: {}", taskResponse.error());
      throw new OptimizeRuntimeException(taskResponse.error().toString());
    }

    if (taskResponse.response() != null) {
      final List<String> failures = taskResponse.response().failures();
      if (failures != null && !failures.isEmpty()) {
        log.error("Opensearch task contains failures: {}", failures);
        throw new OptimizeRuntimeException(failures.toString());
      }
    }
  }
}
