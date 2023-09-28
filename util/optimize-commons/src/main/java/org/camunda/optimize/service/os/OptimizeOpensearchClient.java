/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.OpensearchCustomHeaderProvider;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.elasticsearch.client.RequestOptions;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.util.ObjectBuilderBase;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * This Client serves as the main opensearch client to be used from application code.
 * <p>
 */
@Slf4j
@Conditional(OpenSearchCondition.class)
public class OptimizeOpensearchClient implements ConfigurationReloadable {
  private static final int DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY = 30;
  private static final String INDEX_METHOD = "index";

  @Getter
  private final OpenSearchClient databaseClient;
  @Getter
  private OptimizeIndexNameService indexNameService;

  private RequestOptionsProvider requestOptionsProvider;

  @Setter
  private int snapshotInProgressRetryDelaySeconds = DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY;

  public OptimizeOpensearchClient(final OpenSearchClient databaseClient,
                                  final OptimizeIndexNameService indexNameService) {
    this(databaseClient, indexNameService, new RequestOptionsProvider());
  }

  public OptimizeOpensearchClient(final OpenSearchClient databaseClient,
                                  final OptimizeIndexNameService indexNameService,
                                  final RequestOptionsProvider requestOptionsProvider) {
    this.databaseClient = databaseClient;
    this.indexNameService = indexNameService;
    this.requestOptionsProvider = requestOptionsProvider;
  }

  public final void close() throws IOException {
    // TODO do nothing for now
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
    this.indexNameService = context.getBean(OptimizeIndexNameService.class);
    final OpensearchCustomHeaderProvider customHeaderProvider =
      context.getBean(OpensearchCustomHeaderProvider.class); // TODO check if and when Custom Header is necessary
    this.requestOptionsProvider = new RequestOptionsProvider(customHeaderProvider.getPlugins(), configurationService);
  }

  // TODO check if there's a better way to do this, the problem is that RequestBase does not have an index method
  private <T extends RequestBase> T applyIndexPrefix(T request) {
    try {
      // Get the index method
      Method indexMethod = request.getClass().getMethod(INDEX_METHOD);
      String currentIndex = (String) indexMethod.invoke(request);

      String fullyQualifiedIndexName = indexNameService.getOptimizeIndexAliasForIndex(currentIndex);

      // Invoke the setter method
      Method setIndexMethod = request.getClass().getMethod(INDEX_METHOD, String.class);
      setIndexMethod.invoke(request, fullyQualifiedIndexName);
      return request;
    } catch (NoSuchMethodException e) {
      throw new OptimizeRuntimeException("The object does not have an index() method.");
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException("Failed to invoke the index() method.");
    }
  }

  // TODO revisit when schema is done OPT-7229
  public RequestOptions requestOptions() {
    return requestOptionsProvider.getRequestOptions();
  }

  public record IdIndexEntity<A>(String id, String index, A entity) {
  }

  public interface ExceptionSupplier<R> {
    R get() throws Exception;
  }

  public <A> IndexResponse index(IndexRequest.Builder<A> requestBuilder) {
    return safeIndex(requestBuilder, e -> defaultPersistErrorMessage(getIndex(requestBuilder)));
  }

  private <A> IndexResponse safeIndex(IndexRequest.Builder<A> requestBuilder,
                                      Function<Exception, String> errorMessageSupplier) {
    return safe(() -> databaseClient.index(applyIndexPrefix(requestBuilder.build())), errorMessageSupplier);
  }

  private <R> R safe(ExceptionSupplier<R> supplier, Function<Exception, String> errorMessage) {
    try {
      return supplier.get();
    } catch (Exception e) {
      final String message = errorMessage.apply(e);
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static String defaultPersistErrorMessage(String index) {
    return String.format("Failed to persist index: %s", index);
  }

  private String getIndex(ObjectBuilderBase builder) {
    try {
      Field indexField = builder.getClass().getDeclaredField(INDEX_METHOD);
      return indexField.get(builder).toString();
    } catch (Exception e) {
      log.error(String.format("Failed to get index from %s", builder.getClass().getName()));
      return "FAILED_INDEX";
    }
  }

  public DeleteResponse delete(String index, String id) {
    DeleteRequest.Builder deleteRequestBuilder = new DeleteRequest.Builder()
      .index(index)
      .id(id);

    return safeDelete(deleteRequestBuilder, e -> defaultDeleteErrorMessage(index));
  }

  private DeleteResponse safeDelete(DeleteRequest.Builder requestBuilder,
                                    Function<Exception, String> errorMessageSupplier) {
    return safe(() -> databaseClient.delete(applyIndexPrefix(requestBuilder.build())), errorMessageSupplier);
  }

  private static String defaultDeleteErrorMessage(String index) {
    return String.format("Failed to delete index: %s", index);
  }

  // TODO the methods below are copied from operate and they will likely be needed as we write our opensearch
  //  writers, therefore leaving them in for now

//  private DeleteByQueryResponse safeDelete(DeleteByQueryRequest.Builder requestBuilder, Function<Exception, String>
//  errorMessageSupplier) {
//    return safe(() -> databaseClient.deleteByQuery(applyIndexPrefix(requestBuilder.build())), errorMessageSupplier);
//  }
//
//  public <A> UpdateResponse<Void> update(UpdateRequest.Builder<Void, A> requestBuilder, Function<Exception, String>
//  errorMessageSupplier) {
//    return safeUpdate(requestBuilder, Void.class, errorMessageSupplier);
//  }

//  public record AggregationValue(String key, long count){}
//  public record AggregationResult(boolean error, List<AggregationValue> values, Long totalDocs){}
//
//
//  private static  String defaultSearchErrorMessage(String index) {
//    return String.format("Failed to search index: %s", index);
//  }
//
//
//  public static <R> R withOptimizeRuntimeException(ExceptionSupplier<R> supplier) {
//    try {
//      return supplier.get();
//    } catch (Exception e) {
//      throw new OptimizeRuntimeException(e.getMessage(), e.getCause());
//    }
//  }
//
//  private <A, R> UpdateResponse<R> safeUpdate(UpdateRequest.Builder<R, A> requestBuilder, Class<R> entityClass,
//  Function<Exception, String> errorMessageSupplier) {
//    return safe(() -> databaseClient.update(applyIndexPrefix(requestBuilder.build()), entityClass),
//    errorMessageSupplier);
//  }
//
//  private <R> SearchResponse<R> safeSearch(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
//    return safe(() -> databaseClient.search(applyIndexPrefix(requestBuilder.build()), entityClass), (e) ->
//    defaultSearchErrorMessage(getIndex(requestBuilder)));
//  }
//
//  private <R> List<R> safeScroll(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
//    return safe(() -> OptimizeOpenSearchUtil.scroll(applyIndexPrefix(requestBuilder.build()), entityClass,
//                                                    databaseClient
//    ), (e) -> defaultSearchErrorMessage(getIndex(requestBuilder)));
//  }
//
//  private <R> List<R> safeScroll(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<Map<String,
//  Aggregate>> aggsConsumer) {
//    return safe(() -> OptimizeOpenSearchUtil.scroll(requestBuilder, entityClass, databaseClient, null,
//    aggsConsumer), (e) -> defaultSearchErrorMessage(getIndex(requestBuilder)));
//  }
//
//  private <R> void safeScrollWith(SearchRequest.Builder requestBuilder, Class<R> entityClass,
//  Consumer<List<Hit<R>>> hitsConsumer, Consumer<Map<String, Aggregate>> aggsConsumer) {
//    safe(() -> {
//      OptimizeOpenSearchUtil.scrollWith(requestBuilder, databaseClient, hitsConsumer, aggsConsumer, null,
//      entityClass);
//      return null;
//    }, (e) -> defaultSearchErrorMessage(getIndex(requestBuilder)));
//  }
//
//  public <R> SearchResponse<R> search(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
//    return safeSearch(requestBuilder, entityClass);
//  }
//
//  public <R> List<R> scroll(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
//    return safeScroll(requestBuilder, entityClass);
//  }
//
//  public <R> List<R> scroll(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<Map<String,
//  Aggregate>> aggsConsumer) {
//    return safeScroll(requestBuilder, entityClass, aggsConsumer);
//  }
//
//  public <R> void scrollWith(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<List<Hit<R>>>
//  hitsConsumer) {
//    safeScrollWith(requestBuilder, entityClass, hitsConsumer, null);
//  }
//
//  public <R> List<IdIndexEntity<R>> searchWithIdIndex(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
//    List<IdIndexEntity<R>> result = new ArrayList<>();
//    Function<Hit<R>, IdIndexEntity<R>> hitMapper = hit -> new IdIndexEntity<R>(hit.id(), hit.index(), hit.source());
//    Consumer<List<Hit<R>>> hitConsumer = hits -> result.addAll(hits.stream().map(hitMapper).toList());
//
//    safeScrollWith(requestBuilder, entityClass, hitConsumer, null);
//
//    return result;
//  }
//
//  public <R> R searchUnique(SearchRequest.Builder requestBuilder, Class<R> entityClass, String key) {
//    final SearchResponse<R> response = search(requestBuilder, entityClass);
//
//    if (response.hits().total().value() == 1) {
//      return response.hits().hits().get(0).source();
//    } else if (response.hits().total().value() > 1) {
//      throw new NotFoundException(String.format("Could not find unique %s with key '%s'.", getIndex(requestBuilder)
//      , key));
//    } else {
//      throw new NotFoundException(String.format("Could not find %s with key '%s'.", getIndex(requestBuilder), key));
//    }
//  }
//
//  public AggregationResult searchWithAggregation(SearchRequest.Builder requestBuilder, String aggregationName) {
//    final Aggregate aggregate;
//
//    try {
//      aggregate = search(requestBuilder, Object.class).aggregations().get(aggregationName);
//    } catch (OptimizeRuntimeException e) {
//      return new AggregationResult(true, null, null);
//    }
//
//    if (aggregate == null) {
//      throw new OptimizeRuntimeException("Search with aggregation returned no aggregation");
//    }
//
//    if (!aggregate.isSterms()) {
//      throw new OptimizeRuntimeException("Unexpected response for aggregations");
//    }
//
//    final List<StringTermsBucket> buckets = aggregate.sterms().buckets().array();
//
//    final List<AggregationValue> values = buckets.stream()
//      .map(bucket -> new AggregationValue(bucket.key(), bucket.docCount()))
//      .toList();
//
//    long sumOfOtherDocCounts = aggregate.sterms().sumOtherDocCount(); // size of documents not in result
//    long total = sumOfOtherDocCounts + values.size(); // size of result + other docs
//    return new AggregationResult(false, values, total);
//  }
//
//  public Map<String, String> getIndexNames(String index, Collection<String> ids) {
//    final Map<String, String> result = new HashMap<>();
//    var searchRequestBuilder = new SearchRequest.Builder()
//      .index(index)
//      .query(ids(ids.stream().toList()))
//      .source(s -> s.fetch(false));
//
//    Consumer<List<Hit<Void>>> hitsConsumer = hits -> hits.forEach(
//      hit -> result.put(hit.id(), hit.index())
//    );
//
//    safeScrollWith(searchRequestBuilder, Void.class, hitsConsumer, null);
//
//    return result;
//  }
//
//
//
//  public DeleteByQueryResponse delete(String index, String field, String value) {
//    var deleteRequestBuilder = new DeleteByQueryRequest.Builder()
//      .index(index)
//      .query(term(field, value));
//
//    return safeDelete(deleteRequestBuilder, e -> defaultDeleteErrorMessage(index));
//  }
//
//  public void bulk(BulkRequest.Builder bulkRequestBuilder) {
//    withOptimizeRuntimeException(() -> {
//      OptimizeOpenSearchUtil.processBulkRequest(databaseClient, applyIndexPrefix(bulkRequestBuilder.build()));
//      return null;
//    });
//  }
//
//  public static Query ids(List<String> ids) {
//    return IdsQuery.of(q -> q.values(nonNull(ids)))._toQuery();
//  }
//
//  private static <A> List<A> nonNull(Collection<A> items) {
//    return items.stream().filter(Objects::nonNull).toList();
//  }
//
//  public static <A> Query term(String field, A value, Function<A, FieldValue> toFieldValue) {
//    return TermQuery.of(q -> q.field(field).value(toFieldValue.apply(value)))._toQuery();
//  }
//
//  public static Query term(String field, String value) {
//    return term(field, value, FieldValue::of);
//  }

}
