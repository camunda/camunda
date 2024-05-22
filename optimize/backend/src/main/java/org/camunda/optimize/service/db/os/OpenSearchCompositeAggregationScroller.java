/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregate;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

@Slf4j
public class OpenSearchCompositeAggregationScroller {

  private OptimizeOpenSearchClient osClient;
  private SearchRequest.Builder searchRequestBuilder;
  private Consumer<CompositeBucket> compositeBucketConsumer;
  private LinkedList<String> pathToAggregation;
  private Query query;
  private int requestSize;
  private List<String> indices;

  private Map<String, Aggregation> aggregations = new HashMap<>();

  public OpenSearchCompositeAggregationScroller aggregations(
      final Map<String, Aggregation> aggregations) {
    this.aggregations.putAll(aggregations);
    return this;
  }

  public OpenSearchCompositeAggregationScroller query(final Query query) {
    this.query = query;
    return this;
  }

  public OpenSearchCompositeAggregationScroller index(final List<String> indices) {
    this.indices = indices;
    return this;
  }

  public OpenSearchCompositeAggregationScroller size(final int size) {
    this.requestSize = size;
    return this;
  }

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
    final String errorMessage =
        String.format(
            "Was not able to get next page of %s aggregation.", pathToAggregation.getLast());
    searchRequestBuilder =
        new SearchRequest.Builder()
            .index(indices)
            .query(query)
            .aggregations(aggregations)
            .size(requestSize);

    final SearchResponse searchResponse =
        osClient.search(searchRequestBuilder, SimpleDefinitionDto.class, errorMessage);

    final CompositeAggregate compositeAggregationResult =
        extractCompositeAggregationResult(searchResponse);

    Map<String, String> convertedCompositeBucketConsumer =
        compositeAggregationResult.afterKey().entrySet().stream()
            // Below is a workaround for a known java issue
            // https://bugs.openjdk.org/browse/JDK-8148463
            .collect(
                HashMap::new,
                (m, v) -> m.put(v.getKey(), v.getValue().to(String.class)),
                HashMap::putAll);
    Aggregation currentAggregations = getCurrentAggregations();

    CompositeAggregation prevCompositeAggregation = currentAggregations.composite();

    // find aggregation and adjust after key for next invocation
    CompositeAggregation upgradedCompositeAggregation =
        new CompositeAggregation.Builder()
            .sources(prevCompositeAggregation.sources())
            .size(prevCompositeAggregation.size())
            .after(convertedCompositeBucketConsumer)
            .build();

    this.aggregations.put(
        pathToAggregation.get(0),
        Aggregation.of(
            a ->
                a.composite(upgradedCompositeAggregation)
                    .aggregations(currentAggregations.aggregations())));

    return compositeAggregationResult.buckets().array();
  }

  private CompositeAggregate extractCompositeAggregationResult(
      final SearchResponse searchResponse) {
    Map<String, Aggregate> aggregations = searchResponse.aggregations();
    // find aggregation response
    for (int i = 0; i < pathToAggregation.size() - 1; i++) {
      Aggregate agg = aggregations.get(pathToAggregation.get(i));
      aggregations = agg.nested().aggregations();
    }
    return aggregations.get(pathToAggregation.getLast()).composite();
  }

  private Aggregation getCurrentAggregations() {
    Map<String, Aggregation> aggCol = this.aggregations;
    Aggregation currentAggregationFromPath;

    for (int i = 0; i < pathToAggregation.size() - 1; i++) {
      if (aggCol.containsKey(pathToAggregation.get(i))) {
        currentAggregationFromPath = aggCol.get(pathToAggregation.get(i));
      } else {
        throw new OptimizeRuntimeException(
            String.format(
                "Could not find aggregation [%s] in aggregation path.", pathToAggregation.get(i)));
      }
      aggCol = currentAggregationFromPath.aggregations();
    }

    if (aggCol.containsKey(pathToAggregation.getLast())) {
      currentAggregationFromPath = aggCol.get(pathToAggregation.getLast());
    } else {
      throw new OptimizeRuntimeException(
          String.format(
              "Could not find composite aggregation [%s] in aggregation path.",
              pathToAggregation.getLast()));
    }
    return currentAggregationFromPath;
  }

  public OpenSearchCompositeAggregationScroller setClient(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
    return this;
  }

  public OpenSearchCompositeAggregationScroller setCompositeBucketConsumer(
      final Consumer<CompositeBucket> compositeBucketConsumer) {
    this.compositeBucketConsumer = compositeBucketConsumer;
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
  public OpenSearchCompositeAggregationScroller setPathToAggregation(String... pathToAggregation) {
    this.pathToAggregation = new LinkedList<>(Arrays.asList(pathToAggregation));
    return this;
  }
}
