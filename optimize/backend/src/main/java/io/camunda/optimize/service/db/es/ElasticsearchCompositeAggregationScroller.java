/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;

public class ElasticsearchCompositeAggregationScroller {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ElasticsearchCompositeAggregationScroller.class);
  private OptimizeElasticsearchClient esClient;
  private SearchRequest searchRequest;
  private Consumer<CompositeBucket> compositeBucketConsumer;
  private Function<Map<String, FieldValue>, SearchRequest> searchRequestProvider;
  private LinkedList<String> pathToAggregation;

  public static ElasticsearchCompositeAggregationScroller create() {
    return new ElasticsearchCompositeAggregationScroller();
  }

  public void consumeAllPages() {
    boolean pageConsumed;
    do {
      pageConsumed = consumePage();
    } while (pageConsumed);
  }

  /**
   * Consumes next page of the composite aggregation.
   *
   * @return {@code true} if a page was present, {@code false} else
   */
  public boolean consumePage() {
    final Buckets<CompositeBucket> currentPage = getNextPage();
    currentPage.array().forEach(compositeBucketConsumer);
    return !currentPage.array().isEmpty();
  }

  private Buckets<CompositeBucket> getNextPage() {
    try {
      final SearchResponse<?> searchResponse = esClient.search(searchRequest, Object.class);
      final CompositeAggregate compositeAggregate =
          extractCompositeAggregationResult(searchResponse);
      // find aggregation and adjust after key for next invocation
      searchRequest = searchRequestProvider.apply(compositeAggregate.afterKey());
      return compositeAggregate.buckets();
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to get next page of %s aggregation.", pathToAggregation.getLast());
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(e)) {
        LOG.info(
            "Was not able to get next page of {} aggregation because at least one instance from {} does not exist.",
            pathToAggregation.getLast(),
            searchRequest.index());
        return Buckets.of(b -> b.array(List.of()));
      }
      throw e;
    }
  }

  private CompositeAggregate extractCompositeAggregationResult(
      final SearchResponse<?> searchResponse) {
    Map<String, Aggregate> aggregations = searchResponse.aggregations();
    // find aggregation response
    for (int i = 0; i < pathToAggregation.size() - 1; i++) {
      final Aggregate agg = aggregations.get(pathToAggregation.get(i));
      aggregations = agg.nested().aggregations();
    }
    return aggregations.get(pathToAggregation.getLast()).composite();
  }

  public ElasticsearchCompositeAggregationScroller setSearchRequest(
      final SearchRequest searchRequest) {
    this.searchRequest = searchRequest;
    return this;
  }

  public ElasticsearchCompositeAggregationScroller setEsClient(
      final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
    return this;
  }

  public ElasticsearchCompositeAggregationScroller setCompositeBucketConsumer(
      final Consumer<CompositeBucket> compositeBucketConsumer) {
    this.compositeBucketConsumer = compositeBucketConsumer;
    return this;
  }

  public ElasticsearchCompositeAggregationScroller setFunction(
      final Function<Map<String, FieldValue>, SearchRequest> generateSR) {
    this.searchRequestProvider = generateSR;
    return this;
  }

  /**
   * In order to be able to access the composite aggregation the scroller needs to know where to
   * find the composite aggregation. The path can be stated in this method:
   *
   * <p>Example: Aggregation: nested("fooNested",..).subAggregation(composite("myComposite")..)
   * Respective call: setPathToAggregation("fooNested", "myComposite")
   *
   * @param pathToAggregation a path to where to find the composite aggregation
   * @return the scroller object
   */
  public ElasticsearchCompositeAggregationScroller setPathToAggregation(
      final String... pathToAggregation) {
    this.pathToAggregation = new LinkedList<>(Arrays.asList(pathToAggregation));
    return this;
  }
}
