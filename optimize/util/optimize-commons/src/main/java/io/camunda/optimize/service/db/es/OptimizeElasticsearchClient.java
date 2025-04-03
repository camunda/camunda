/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.util.WorkaroundUtil.replaceNullWithNanInAggregations;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
import static java.util.stream.Collectors.groupingBy;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.analysis.TokenChar;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch.cluster.HealthRequest;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndicesSettingsResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest.Builder;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.PutTemplateRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RolloverRequest;
import co.elastic.clients.elasticsearch.indices.RolloverResponse;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.DeleteSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.DeleteSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.GetRepositoryRequest;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotResponse;
import co.elastic.clients.elasticsearch.tasks.ListRequest;
import co.elastic.clients.elasticsearch.tasks.ListResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.SimpleJsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpGenerator;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.DefaultTransportOptions;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.DataImportSourceType;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto.Fields;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.es.schema.TransportOptionsProvider;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.es.ElasticsearchClientBuilder;
import io.camunda.search.connect.plugin.PluginRepository;
import jakarta.json.spi.JsonProvider;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * This Client serves as the main elasticsearch client to be used from application code.
 *
 * <p>The exposed methods correspond to the interface of the {@link ElasticsearchClient}, any
 * requests passed are expected to contain just the {@link IndexMappingCreator#getIndexName()} value
 * as index name. The client will care about injecting the current index prefix.
 *
 * <p>For low level operations it still exposes the underlying {@link ElasticsearchClient}, as well
 * as the {@link OptimizeIndexNameService}.
 */
public class OptimizeElasticsearchClient extends DatabaseClient {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeElasticsearchClient.class);
  private RestClient restClient;
  private final ObjectMapper objectMapper;
  private ElasticsearchClient esClient;
  private ElasticsearchAsyncClient elasticsearchAsyncClient;
  private TransportOptionsProvider transportOptionsProvider;

  public OptimizeElasticsearchClient(
      final RestClient restClient,
      final ObjectMapper objectMapper,
      final ElasticsearchClient esClient,
      final OptimizeIndexNameService indexNameService) {
    this(restClient, objectMapper, esClient, indexNameService, new TransportOptionsProvider());
  }

  public OptimizeElasticsearchClient(
      final RestClient restClient,
      final ObjectMapper objectMapper,
      final ElasticsearchClient esClient,
      final OptimizeIndexNameService indexNameService,
      final TransportOptionsProvider transportOptionsProvider) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.esClient = esClient;
    this.indexNameService = indexNameService;
    this.transportOptionsProvider = transportOptionsProvider;
    elasticsearchAsyncClient =
        new ElasticsearchAsyncClient(esClient._transport(), esClient._transportOptions());
  }

  public final ElasticsearchClient esWithTransportOptions() {
    return esClient.withTransportOptions(transportOptionsProvider.getTransportOptions());
  }

  public final GetMappingResponse getMapping(
      final Builder getMappingsRequest, final String... indexes) throws IOException {
    getMappingsRequest.index(Arrays.stream(convertToPrefixedAliasNames(indexes)).toList());
    return esWithTransportOptions().indices().getMapping(getMappingsRequest.build());
  }

  public void updateSettings(final PutIndicesSettingsRequest request) throws IOException {
    esWithTransportOptions().indices().putSettings(request);
  }

  // We need this logic because the Elasticsearch Java client depends on the existence
  // of the `tokenChars` field. Optimize indices created using the Elasticsearch 7 client
  // did not have this field, so we require custom logic to get their index settings.
  // Once all indices have this field specified, this method can be removed
  public GetIndicesSettingsResponse getOldIndexSettings() throws IOException {
    final String endpoint = "/" + indexNameService.getIndexPrefix() + "*/_settings";
    final Request request = new Request(HttpGet.METHOD_NAME, endpoint);
    request.setOptions(transportOptionsProvider.getRequestOptions());
    final Response response = restClient.performRequest(request);
    final Map<String, Map> responseContentAsMap =
        OPTIMIZE_MAPPER.readValue(response.getEntity().getContent(), Map.class);
    for (final Map contentEntry : responseContentAsMap.values()) {
      final Map settings = (Map) contentEntry.get("settings");
      if (settings == null) {
        continue;
      }
      final Map index = (Map) settings.get("index");
      if (index == null) {
        continue;
      }
      final Map analysis = (Map) index.get("analysis");
      if (analysis == null) {
        continue;
      }
      final Map analyzer = (Map) ((Map) analysis.get("analyzer")).get("is_present_analyzer");
      if (!List.class.isInstance(analyzer.get("filter"))) {
        analyzer.put("filter", List.of(analyzer.get("filter")));
      }
      final Map lowercaseNgram = (Map) ((Map) analysis.get("analyzer")).get("lowercase_ngram");
      if (!List.class.isInstance(lowercaseNgram.get("filter"))) {
        lowercaseNgram.put("filter", List.of(lowercaseNgram.get("filter")));
      }
      final Map tokenizer = (Map) analysis.get("tokenizer");
      if (tokenizer == null) {
        continue;
      }
      final Map ngramTokenizer = (Map) tokenizer.get("ngram_tokenizer");
      if (!ngramTokenizer.containsKey("token_chars")) {
        ngramTokenizer.put(
            "token_chars",
            List.of(
                TokenChar.Letter.jsonValue(),
                TokenChar.Digit.jsonValue(),
                TokenChar.Whitespace.jsonValue(),
                TokenChar.Punctuation.jsonValue(),
                TokenChar.Symbol.jsonValue()));
      }
    }
    return GetIndicesSettingsResponse.of(
        b -> {
          try {
            return b.withJson(
                JsonProvider.provider()
                    .createParser(
                        new StringReader(OPTIMIZE_MAPPER.writeValueAsString(responseContentAsMap))),
                SimpleJsonpMapper.INSTANCE);
          } catch (final JsonProcessingException e) {
            throw new OptimizeRuntimeException(
                "An error occurred during retrieval of old index settings.", e);
          }
        });
  }

  public final <T> ScrollResponse<T> scroll(
      final ScrollRequest searchScrollRequest, final Class<T> clas) throws IOException {
    return esWithTransportOptions().scroll(searchScrollRequest, clas);
  }

  public void verifyRepositoryExists(final GetRepositoryRequest getRepositoriesRequest)
      throws IOException, ElasticsearchException {
    esWithTransportOptions().snapshot().getRepository(getRepositoriesRequest);
  }

  public GetSnapshotResponse getSnapshots(final GetSnapshotRequest getSnapshotsRequest)
      throws IOException {
    return esWithTransportOptions().snapshot().get(getSnapshotsRequest);
  }

  public final <T> MgetResponse<T> mget(final MgetRequest multiGetRequest, final Class<T> tClass)
      throws IOException {
    return esWithTransportOptions().mget(multiGetRequest, tClass);
  }

  public CompletableFuture<CreateSnapshotResponse> triggerSnapshotAsync(
      final CreateSnapshotRequest createSnapshotRequest) {
    return elasticsearchAsyncClient.snapshot().create(createSnapshotRequest);
  }

  public HealthResponse getClusterHealth(final HealthRequest request) throws IOException {
    return esWithTransportOptions().cluster().health(request);
  }

  public CompletableFuture<DeleteSnapshotResponse> deleteSnapshotAsync(
      final DeleteSnapshotRequest deleteSnapshotRequest) {
    return elasticsearchAsyncClient.snapshot().delete(deleteSnapshotRequest);
  }

  public final DeleteResponse delete(final DeleteRequest deleteRequest) throws IOException {
    return esWithTransportOptions().delete(deleteRequest);
  }

  public Response performRequest(final Request request) throws IOException {
    request.setOptions(transportOptionsProvider.getRequestOptions());
    return restClient.performRequest(request);
  }

  public final <T> IndexResponse index(final IndexRequest<T> indexRequest) throws IOException {
    return esWithTransportOptions().index(indexRequest);
  }

  public <T> void doImportBulkRequestWithList(
      final String importItemName,
      final Collection<T> entityCollection,
      final BiConsumer<BulkRequest.Builder, T> addDtoToRequestConsumer,
      final boolean retryRequestIfNestedDocLimitReached) {
    if (entityCollection.isEmpty()) {
      LOG.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final BulkRequest bulkRequest =
          BulkRequest.of(
              b -> {
                entityCollection.forEach(dto -> addDtoToRequestConsumer.accept(b, dto));
                return b;
              });
      doBulkRequest(bulkRequest, importItemName, retryRequestIfNestedDocLimitReached);
    }
  }

  public final <T> GetResponse<T> get(final GetRequest getRequest, final Class<T> tClass)
      throws IOException {
    return esWithTransportOptions().get(getRequest, tClass);
  }

  public final boolean exists(final IndexMappingCreator indexMappingCreator) throws IOException {
    return exists(
        GetIndexRequest.of(
            b ->
                b.index(
                    indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(
                        indexMappingCreator))));
  }

  public final boolean exists(final ExistsRequest getRequest) throws IOException {
    return esWithTransportOptions().indices().exists(getRequest).value();
  }

  public final boolean exists(final GetIndexRequest getRequest) throws IOException {
    try {
      return !esWithTransportOptions().indices().get(getRequest).result().isEmpty();
    } catch (final ElasticsearchException e) {
      if (e.getMessage().contains("index_not_found_exception")) {
        return false;
      }
      throw e;
    }
  }

  public final boolean templateExists(final String indexName) throws IOException {
    return esWithTransportOptions()
        .indices()
        .existsTemplate(b -> b.name(List.of(convertToPrefixedAliasNames(new String[] {indexName}))))
        .value();
  }

  public void deleteIndexTemplateByIndexTemplateName(final String indexTemplateName) {
    final String prefixedIndexTemplateName =
        indexNameService.getOptimizeIndexAliasForIndex(indexTemplateName);
    LOG.debug("Deleting index template [{}].", prefixedIndexTemplateName);
    try {
      esWithTransportOptions().indices().deleteTemplate(b -> b.name(prefixedIndexTemplateName));
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Could not delete index template [%s]!", prefixedIndexTemplateName);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    LOG.debug("Successfully deleted index template [{}].", prefixedIndexTemplateName);
  }

  public void createIndex(final CreateIndexRequest request) throws IOException {
    esWithTransportOptions().indices().create(request);
  }

  public long countWithoutPrefix(final CountRequest request)
      throws IOException, InterruptedException {
    final int maxNumberOfRetries = 10;
    final int waitIntervalMillis = 3000;
    int retryAttempts = 0;
    while (retryAttempts < maxNumberOfRetries) {
      final CountResponse countResponse = esWithTransportOptions().count(request);
      if (countResponse.shards().failed().intValue() > 0) {
        LOG.info(
            "Not all shards returned successful for count response from indices: {}",
            request.index());
        retryAttempts++;
        Thread.sleep(waitIntervalMillis);
      } else {
        return countResponse.count();
      }
    }
    throw new OptimizeRuntimeException(
        String.format("Could not determine count from indices: %s", request.index()));
  }

  public ElasticsearchClient elasticsearchClient() {
    return esClient;
  }

  public UpdateByQueryResponse submitUpdateTask(final UpdateByQueryRequest request)
      throws IOException {
    return esWithTransportOptions().updateByQuery(request);
  }

  public final UpdateByQueryResponse updateByQuery(
      final UpdateByQueryRequest.Builder updateByQueryRequestBuilder, final List<String> indexes)
      throws IOException {
    return esWithTransportOptions()
        .updateByQuery(
            updateByQueryRequestBuilder
                .index(indexes.stream().map(this::convertToPrefixedAliasName).toList())
                .build());
  }

  public ListResponse getTaskList(final ListRequest request) throws IOException {
    return esWithTransportOptions().tasks().list(request);
  }

  @Override
  public Map<String, Set<String>> getAliasesForIndexPattern(final String indexNamePattern) {
    try {
      return getAlias(indexNamePattern).result().entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().aliases().keySet()));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Set<String> getAllIndicesForAlias(final String aliasName) {
    try {
      return esWithTransportOptions()
          .indices()
          .getAlias((b) -> b.name(aliasName))
          .result()
          .keySet();
    } catch (final ElasticsearchException e) {
      if (e.response().status() == 404) {
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
        RolloverRequest.of(
            b ->
                b.alias(convertToPrefixedAliasName(indexAliasName))
                    .conditions(builder -> builder.maxSize(maxIndexSizeGB + "gb")));
    LOG.info("Executing rollover request on {}", indexAliasName);
    try {
      final RolloverResponse rolloverResponse = rollover(rolloverRequest);
      if (rolloverResponse.rolledOver()) {
        LOG.info(
            "Index with alias {} has been rolled over. New index name: {}",
            indexAliasName,
            rolloverResponse.newIndex());
      } else {
        LOG.debug("Index with alias {} has not been rolled over.", indexAliasName);
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
    final String[] allIndicesForAlias = getAllIndicesForAlias(indexAlias).toArray(String[]::new);
    deleteIndexByRawIndexNames(allIndicesForAlias);
  }

  @Override
  public long countWithoutPrefix(final String unprefixedIndex) {
    try {
      return countWithoutPrefix(CountRequest.of(c -> c.index(unprefixedIndex)));
    } catch (final IOException | InterruptedException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  @Override
  public void refresh(final String indexPattern) {
    final RefreshRequest.Builder builder = new RefreshRequest.Builder();
    applyIndexPrefixes(builder, List.of(indexPattern));
    try {
      esWithTransportOptions().indices().refresh(builder.build());
    } catch (final IOException e) {
      LOG.error("Could not refresh Optimize indexes!", e);
      throw new OptimizeRuntimeException("Could not refresh Optimize indexes!", e);
    }
  }

  @Override
  public final List<String> getAllIndexNames() throws IOException {
    return esWithTransportOptions()
        .indices()
        .get(
            GetIndexRequest.of(
                g ->
                    g.index(
                        List.of(
                            convertToPrefixedAliasNames(
                                GetIndexRequest.of(r -> r.index("*"))
                                    .index()
                                    .toArray(new String[] {}))))))
        .result()
        .keySet()
        .stream()
        .toList();
  }

  @Override
  public List<String> addPrefixesToIndices(final String... indexes) {
    return List.of(convertToPrefixedAliasNames(indexes));
  }

  @Override
  public String getDatabaseVersion() {
    try {
      return esWithTransportOptions().info().version().number();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setDefaultRequestOptions() {
    try {
      esClient.withTransportOptions(DefaultTransportOptions.EMPTY).info();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void update(final String indexName, final String entityId, final ScriptData script) {
    try {
      update(
          OptimizeUpdateRequestBuilderES.of(
              b ->
                  b.optimizeIndex(this, indexNameService.getOptimizeIndexAliasForIndex(indexName))
                      .id(entityId)
                      .script(
                          sb ->
                              sb.inline(
                                  ib ->
                                      ib.params(
                                              script.params().entrySet().stream()
                                                  .collect(
                                                      Collectors.toMap(
                                                          Entry::getKey,
                                                          e -> JsonData.of(e.getValue()))))
                                          .source(script.scriptString())
                                          .lang(ScriptLanguage.Painless)))
                      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)),
          Object.class);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "The error occurs while updating OpenSearch entity %s with id %s",
              indexName, entityId);
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void executeImportRequestsAsBulk(
      final String bulkRequestName,
      final List<ImportRequestDto> importRequestDtos,
      final Boolean retryFailedRequestsOnNestedDocLimit) {
    final BulkRequest bulkRequest =
        BulkRequest.of(
            b -> {
              final Map<String, List<ImportRequestDto>> requestsByType =
                  importRequestDtos.stream()
                      .filter(this::validateImportName)
                      .collect(groupingBy(ImportRequestDto::getImportName));

              requestsByType.forEach(
                  (type, requests) -> {
                    LOG.debug(
                        "Adding [{}] requests of type {} to bulk request", requests.size(), type);
                    requests.forEach(
                        importRequest -> applyOperationToBulkRequest(b, importRequest));
                  });
              return b;
            });

    doBulkRequest(bulkRequest, bulkRequestName, retryFailedRequestsOnNestedDocLimit);
  }

  @Override
  public Set<String> performSearchDefinitionQuery(
      final String indexName,
      final String definitionXml,
      final String definitionIdField,
      final int maxPageSize,
      final String engineAlias) {
    LOG.debug("Performing " + indexName + " search query!");
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            builder ->
                builder
                    .optimizeIndex(this, indexName)
                    .query(
                        qb ->
                            qb.bool(
                                bb ->
                                    bb.mustNot(
                                            lb ->
                                                lb.exists(
                                                    ExistsQuery.of(eb -> eb.field(definitionXml))))
                                        .must(
                                            lb ->
                                                lb.term(
                                                    tb ->
                                                        tb.field(DEFINITION_DELETED).value(false)))
                                        .must(
                                            lb ->
                                                lb.term(
                                                    tb ->
                                                        tb.field(DATA_SOURCE + "." + Fields.type)
                                                            .value(
                                                                FieldValue.of(
                                                                    DataImportSourceType.ENGINE))))
                                        .must(
                                            lb ->
                                                lb.term(
                                                    tb ->
                                                        tb.field(DATA_SOURCE + "." + Fields.name)
                                                            .value(engineAlias)))))
                    .source(sb -> sb.fetch(false))
                    .sort(
                        sb ->
                            sb.field(
                                FieldSort.of(
                                    fs -> fs.field(definitionIdField).order(SortOrder.Desc))))
                    .size(maxPageSize));
    final SearchResponse<?> searchResponse;
    try {
      // refresh to ensure we see the latest state
      refresh(RefreshRequest.of(rb -> rb.index(addPrefixesToIndices(indexName))));
      searchResponse = search(searchRequest, Object.class);
    } catch (final IOException e) {
      LOG.error("Was not able to search for " + indexName + "!", e);
      throw new OptimizeRuntimeException("Was not able to search for " + indexName + "!", e);
    }

    LOG.debug(indexName + " search query got [{}] results", searchResponse.hits().hits().size());

    return searchResponse.hits().hits().stream().map(Hit::id).collect(Collectors.toSet());
  }

  @Override
  public DatabaseType getDatabaseVendor() {
    return DatabaseType.ELASTICSEARCH;
  }

  @Override
  public void deleteIndexByRawIndexNames(final String... indexNames) {
    final String indexNamesString = Arrays.toString(indexNames);
    LOG.debug("Deleting indices [{}].", indexNamesString);
    dbClientSnapshotFailsafe("DeleteIndex: " + indexNamesString)
        .get(
            () ->
                esWithTransportOptions()
                    .indices()
                    .delete(DeleteIndexRequest.of(b -> b.index(List.of(indexNames)))));
    LOG.debug("Successfully deleted index [{}].", indexNamesString);
  }

  @Override
  public void deleteAllIndexes() {
    deleteIndexByRawIndexNames("_all");
  }

  public long count(final String[] indexNames, final BoolQuery.Builder query) throws IOException {
    return Objects.requireNonNull(
            count(
                CountRequest.of(
                    b -> {
                      final CountRequest.Builder builder =
                          b.index(List.of(convertToPrefixedAliasNames(indexNames)));
                      builder.query(q -> q.bool(query.build()));
                      return b;
                    })))
        .count();
  }

  public final GetAliasResponse getAlias(final GetAliasRequest getAliasesRequest)
      throws IOException {
    return esWithTransportOptions().indices().getAlias(getAliasesRequest);
  }

  private GetAliasResponse getAlias(final String indexNamePattern) throws IOException {
    return esWithTransportOptions()
        .indices()
        .getAlias((b) -> b.index(convertToPrefixedAliasName(indexNamePattern)));
  }

  public final RolloverResponse rollover(final RolloverRequest rolloverRequest) throws IOException {
    return esWithTransportOptions().indices().rollover(rolloverRequest);
  }

  public void createMapping(final PutMappingRequest request) throws IOException {
    esWithTransportOptions().indices().putMapping(request);
  }

  public void createTemplate(final PutTemplateRequest request) throws IOException {
    esWithTransportOptions().indices().putTemplate(request);
  }

  public <T> void deleteIndex(final IndexMappingCreator<T> indexMappingCreator) {
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexMappingCreator);
    deleteIndex(indexAlias);
  }

  public final CountResponse count(final CountRequest countRequest) throws IOException {
    return esWithTransportOptions().count(countRequest);
  }

  private void applyIndexPrefixes(
      final RefreshRequest.Builder request, final List<String> indexes) {
    request.index(List.of(convertToPrefixedAliasNames(indexes.toArray(new String[] {}))));
  }

  public <T> SearchResponse<T> search(final SearchRequest searchRequest, final Class<T> tClass)
      throws IOException {
    return esWithTransportOptions().search(searchRequest, tClass);
  }

  public <T> SearchResponse<T> search(final SearchRequest searchRequest) throws IOException {
    String path = "/" + String.join(",", searchRequest.index()) + "/_search?typed_keys=true";
    if (searchRequest.scroll() != null) {
      path = path + "&scroll=" + searchRequest.scroll().time();
    }
    try {
      final Request request = new Request(HttpPost.METHOD_NAME, path);
      final HttpEntity entity =
          new NStringEntity(extractQuery(searchRequest), ContentType.APPLICATION_JSON);
      request.setEntity(entity);
      final Response response = restClient.performRequest(request);
      final Map map = OPTIMIZE_MAPPER.readValue(response.getEntity().getContent(), Map.class);
      replaceNullWithNanInAggregations(map);
      return SearchResponse.of(
          s -> {
            try {
              return s.withJson(new StringReader(OPTIMIZE_MAPPER.writeValueAsString(map)));
            } catch (final JsonProcessingException e) {
              throw new RuntimeException(e);
            }
          });
    } catch (final ResponseException e) {
      throw new ElasticsearchException(
          "error",
          ErrorResponse.of(
              r -> {
                try {
                  return r.withJson(e.getResponse().getEntity().getContent());
                } catch (final IOException ex) {
                  throw new RuntimeException(ex);
                }
              }));
    }
  }

  String extractQuery(final SearchRequest searchRequest) {
    try {
      final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(OPTIMIZE_MAPPER);

      final StringWriter writer = new StringWriter();
      final JacksonJsonpGenerator generator =
          new JacksonJsonpGenerator(new JsonFactory().createGenerator(writer));
      searchRequest.serialize(generator, jsonpMapper);
      generator.flush();
      return writer.toString();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public ClearScrollResponse clearScroll(final ClearScrollRequest clearScrollRequest) {
    try {
      return esWithTransportOptions().clearScroll(clearScrollRequest);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void applyOperationToBulkRequest(
      final BulkRequest.Builder builder, final ImportRequestDto requestDto) {

    validateOperationParams(requestDto);

    switch (requestDto.getType()) {
      case INDEX ->
          builder.operations(
              bb ->
                  bb.index(
                      bi ->
                          bi.index(addPrefixesToIndices(requestDto.getIndexName()).get(0))
                              .id(requestDto.getId())
                              .document(requestDto.getSource())));
      case UPDATE ->
          builder.operations(
              bb ->
                  bb.update(
                      bi ->
                          bi.index(addPrefixesToIndices(requestDto.getIndexName()).get(0))
                              .id(requestDto.getId())
                              .action(
                                  a ->
                                      a.upsert(requestDto.getSource())
                                          .script(
                                              createDefaultScriptWithPrimitiveParams(
                                                  requestDto.getScriptData().scriptString(),
                                                  requestDto.getScriptData().params())))
                              .retryOnConflict(requestDto.getRetryNumberOnConflict())));
      default -> throw new IllegalStateException("Unexpected value: " + requestDto.getType());
    }
  }

  public static <T> Script createDefaultScriptWithPrimitiveParams(
      final String inlineUpdateScript, final Map<String, T> params) {
    return Script.of(
        b ->
            b.inline(
                i ->
                    i.lang(ScriptLanguage.Painless)
                        .source(inlineUpdateScript)
                        .params(
                            params.entrySet().stream()
                                .collect(
                                    Collectors.toMap(
                                        Entry::getKey, e -> JsonData.of(e.getValue()))))));
  }

  public void doBulkRequest(
      final BulkRequest bulkRequest,
      final String itemName,
      final boolean retryRequestIfNestedDocLimitReached) {
    if (retryRequestIfNestedDocLimitReached) {
      doBulkRequestWithNestedDocHandling(bulkRequest, itemName);
    } else {
      doBulkRequestWithoutRetries(bulkRequest, itemName);
    }
  }

  private void doBulkRequestWithoutRetries(final BulkRequest bulkRequest, final String itemName) {
    if (bulkRequest != null && !bulkRequest.operations().isEmpty()) {
      try {
        final BulkResponse bulkResponse = bulk(bulkRequest);
        if (bulkResponse.errors()) {
          throw new OptimizeRuntimeException(
              String.format(
                  "There were failures while performing bulk on %s.%n%s Message: %s",
                  itemName,
                  getHintForErrorMsg(bulkResponse),
                  bulkResponse.items().stream()
                      .filter(i -> Objects.nonNull(i.error()))
                      .map(
                          i ->
                              i.operationType() + " " + i.error().type() + " " + i.error().reason())
                      .collect(Collectors.joining(" , "))));
        }
      } catch (final IOException e) {
        final String reason =
            String.format("There were errors while performing a bulk on %s.", itemName);
        LOG.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      LOG.debug("Bulkrequest on {} not executed because it contains no actions.", itemName);
    }
  }

  private static String getHintForErrorMsg(final BulkResponse bulkResponse) {
    if (containsNestedDocumentLimitErrorMessage(bulkResponse)) {
      // exception potentially related to nested object limit
      return "If you are experiencing failures due to too many nested documents, try carefully increasing the "
          + "configured nested object limit (es.settings.index.nested_documents_limit) or enabling the skipping of "
          + "documents that have reached this limit during import (import.skipDataAfterNestedDocLimitReached). "
          + "See Optimize documentation for details.";
    }
    return "";
  }

  private static boolean containsNestedDocumentLimitErrorMessage(final BulkResponse bulkResponse) {
    return bulkResponse.items().stream()
        .filter(i -> i.error() != null)
        .map(i -> i.error().reason().contains(NESTED_DOC_LIMIT_MESSAGE))
        .findFirst()
        .orElse(false);
  }

  public final BulkResponse bulk(final BulkRequest bulkRequest) throws IOException {
    return esWithTransportOptions().bulk(bulkRequest);
  }

  private void doBulkRequestWithNestedDocHandling(
      final BulkRequest bulkRequest, final String itemName) {
    if (bulkRequest != null && !bulkRequest.operations().isEmpty()) {
      LOG.info(
          "Executing bulk request on {} items of {}", bulkRequest.operations().size(), itemName);
      try {
        final BulkResponse bulkResponse = bulk(bulkRequest);
        if (!bulkResponse.errors()) {
          return;
        }
        if (containsNestedDocumentLimitErrorMessage(bulkResponse)) {
          final Map<String, List<String>> failedNestedDocLimitItemIdsByIndexName =
              bulkResponse.items().stream()
                  .filter(b -> b.error() != null && b.error().reason() != null && b.id() != null)
                  .filter(
                      responseItem ->
                          responseItem.error().reason().contains(NESTED_DOC_LIMIT_MESSAGE))
                  .collect(
                      Collectors.groupingBy(
                          BulkResponseItem::index,
                          Collectors.mapping(BulkResponseItem::id, Collectors.toList())));

          final Set<String> failedOperationIds =
              failedNestedDocLimitItemIdsByIndexName.values().stream()
                  .flatMap(Collection::stream)
                  .collect(Collectors.toSet());
          LOG.warn(
              "There were failures while performing bulk on {} due to the nested document limit being reached."
                  + " Removing {} failed items and retrying",
              itemName,
              failedOperationIds.size());
          LOG.debug("Failed operation IDs by Index: {}", failedNestedDocLimitItemIdsByIndexName);
          final List<BulkOperation> bulkOperations = new ArrayList<>(bulkRequest.operations());
          bulkOperations.removeIf(
              request -> {
                if (request.isCreate()) {
                  return failedOperationIds.contains(request.create().id());
                } else if (request.isUpdate()) {
                  return failedOperationIds.contains(request.update().id());
                } else if (request.isDelete()) {
                  return failedOperationIds.contains(request.delete().id());
                } else if (request.isIndex()) {
                  return failedOperationIds.contains(request.index().id());
                }
                return false;
              });
          if (!bulkOperations.isEmpty()) {
            doBulkRequestWithNestedDocHandling(
                BulkRequest.of(b -> b.operations(bulkOperations)), itemName);
          }
        } else {
          throw new OptimizeRuntimeException(
              String.format("There were failures while performing bulk on %s", itemName));
        }
      } catch (final IOException e) {
        final String reason =
            String.format("There were errors while performing a bulk on %s.", itemName);
        LOG.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      LOG.debug("Bulkrequest on {} not executed because it contains no actions.", itemName);
    }
  }

  public DeleteByQueryResponse submitDeleteTask(final DeleteByQueryRequest request)
      throws IOException {
    return esWithTransportOptions().deleteByQuery(request);
  }

  public ReindexResponse submitReindexTask(final ReindexRequest request) throws IOException {
    return esWithTransportOptions().reindex(request);
  }

  public final void deleteByQuery(
      final DeleteByQueryRequest.Builder deleteByQueryRequestBuilder, final String... indexes)
      throws IOException {
    esWithTransportOptions()
        .deleteByQuery(
            deleteByQueryRequestBuilder
                .index(Arrays.stream(convertToPrefixedAliasNames(indexes)).toList())
                .build());
  }

  public final <T, R> UpdateResponse<T> update(
      final UpdateRequest<T, R> updateRequest, final Class<T> tClass) throws IOException {
    return esWithTransportOptions().update(updateRequest, tClass);
  }

  public void refresh(final RefreshRequest refreshRequest) throws IOException {
    esWithTransportOptions().indices().refresh(refreshRequest);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    try {
      esWithTransportOptions()._transport().close();
      final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
      esClient =
          ElasticsearchClientBuilder.build(
              configurationService, objectMapper, new PluginRepository());
      restClient =
          ElasticsearchClientBuilder.restClient(configurationService, new PluginRepository());
      indexNameService = context.getBean(OptimizeIndexNameService.class);
      transportOptionsProvider = new TransportOptionsProvider(configurationService);
      elasticsearchAsyncClient =
          new ElasticsearchAsyncClient(esClient._transport(), esClient._transportOptions());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final <T> SearchResponse<T> searchWithoutPrefixing(
      final SearchRequest searchRequest, final Class<T> clas) throws IOException {
    return esWithTransportOptions().search(searchRequest, clas);
  }

  public ElasticsearchClient getEsClient() {
    return esClient;
  }
}
