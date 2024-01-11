/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregate;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class OpenSearchCompositeAggregationScroller {

  private OptimizeOpenSearchClient osClient;
  private SearchRequest.Builder searchRequestBuilder;
  private Consumer<CompositeBucket> compositeBucketConsumer;
  private LinkedList<String> pathToAggregation;


  public static OpenSearchCompositeAggregationScroller create() {
    return new OpenSearchCompositeAggregationScroller();
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
    final List<CompositeBucket> currentPage = getNextPage();
    currentPage.forEach(compositeBucketConsumer);
    return !currentPage.isEmpty();
  }

  private List<CompositeBucket> getNextPage() {
    final String errorMessage = String.format(
      "Was not able to get next page of %s aggregation.", pathToAggregation.getLast()
    );
    final SearchResponse searchResponse = osClient.search(searchRequestBuilder, String.class, errorMessage);

    final CompositeAggregate compositeAggregationResult = extractCompositeAggregationResult(searchResponse);

    Map<String, String> convertedCompositeBucketConsumer = compositeAggregationResult
      .afterKey()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, key -> key.getValue().to(String.class)));

    // find aggregation and adjust after key for next invocation
    CompositeAggregation.Builder upgradedCompositeAggregation = getCompositeAggregationBuilder().after(
      convertedCompositeBucketConsumer);

    redefineSearchReqBuilder(searchRequestBuilder.build());
    searchRequestBuilder.aggregations(pathToAggregation.get(0), upgradedCompositeAggregation.build()._toAggregation());

    return compositeAggregationResult.buckets().array();
  }

  private void redefineSearchReqBuilder(final SearchRequest currentSearchRequest) {
    this.searchRequestBuilder = new SearchRequest.Builder()
      .index(currentSearchRequest.index())
      .size(currentSearchRequest.size())
      .query(currentSearchRequest.query());
  }

  private CompositeAggregate extractCompositeAggregationResult(final SearchResponse searchResponse) {
    Map<String, Aggregate> aggregations = searchResponse.aggregations();
    // find aggregation response
    for (int i = 0; i < pathToAggregation.size() - 1; i++) {
      Aggregate agg = aggregations.get(pathToAggregation.get(i));
      aggregations = agg.nested().aggregations();
    }
    return aggregations.get(pathToAggregation.getLast()).composite();
  }

  private CompositeAggregation.Builder getCompositeAggregationBuilder() {
    Map<String, Aggregation> aggCol = searchRequestBuilder.build().aggregations();
    Aggregation currentAggregationFromPath;

    for (int i = 0; i < pathToAggregation.size() - 1; i++) {

      if (aggCol.containsKey(pathToAggregation.get(i))) {
        currentAggregationFromPath = aggCol.get(pathToAggregation.get(i));
      } else {
        throw new OptimizeRuntimeException(
          String.format("Could not find aggregation [%s] in aggregation path.", pathToAggregation.get(i)));
      }
      aggCol = currentAggregationFromPath.aggregations();
    }

    if (aggCol.containsKey(pathToAggregation.getLast())) {
      currentAggregationFromPath = aggCol.get(pathToAggregation.getLast());
    } else {
      throw new OptimizeRuntimeException(
        String.format("Could not find composite aggregation [%s] in aggregation path.", pathToAggregation.getLast()));
    }

    CompositeAggregation compositeAggregationResult = currentAggregationFromPath.composite();
    return new CompositeAggregation.Builder().sources(compositeAggregationResult.sources())
      .size(compositeAggregationResult.size());
  }

  public OpenSearchCompositeAggregationScroller setSearchRequest(final SearchRequest.Builder searchRequestBuilder) {
    this.searchRequestBuilder = searchRequestBuilder;
    return this;
  }

  public OpenSearchCompositeAggregationScroller setClient(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
    return this;
  }

  public OpenSearchCompositeAggregationScroller setCompositeBucketConsumer(final Consumer<CompositeBucket> compositeBucketConsumer) {
    this.compositeBucketConsumer = compositeBucketConsumer;
    return this;
  }

  /**
   * In order to be able to access the composite aggregation the scroller needs to know
   * where to find the composite aggregation. The path can be stated in this method:
   * <p>
   * Example:
   * Aggregation: nested("fooNested",..).subAggregation(composite("myComposite")..)
   * Respective call: setPathToAggregation("fooNested", "myComposite")
   *
   * @param pathToAggregation a path to where to find the composite aggregation
   * @return the scroller object
   */
  public OpenSearchCompositeAggregationScroller setPathToAggregation(String... pathToAggregation) {
    this.pathToAggregation = new LinkedList<>(Arrays.asList(pathToAggregation));
    return this;
  }

}
