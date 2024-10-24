/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Aggregation.Builder;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregate;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;

public class OpenSearchCompositeAggregationScroller {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OpenSearchCompositeAggregationScroller.class);
  private OptimizeOpenSearchClient osClient;
  private SearchRequest.Builder searchRequestBuilder;
  private Consumer<CompositeBucket> compositeBucketConsumer;
  private LinkedList<String> pathToAggregation;
  private Query query;
  private int requestSize;
  private List<String> indices;

  private Map<String, Aggregation> aggregations;

  public OpenSearchCompositeAggregationScroller aggregations(
      final Map<String, Aggregation> aggregations) {
    this.aggregations = new HashMap<>(aggregations);
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
    requestSize = size;
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

    try {
      final SearchResponse searchResponse =
          osClient.search(searchRequestBuilder, SimpleDefinitionDto.class, errorMessage);

      final CompositeAggregate compositeAggregationResult =
          extractCompositeAggregationResult(searchResponse);

      final Map<String, String> safeAfterKeyMap =
          compositeAggregationResult.afterKey().entrySet().stream()
              // Below is a workaround for a known java issue
              // https://bugs.openjdk.org/browse/JDK-8148463
              .collect(
                  HashMap::new,
                  (m, v) -> m.put(v.getKey(), v.getValue().to(String.class)),
                  HashMap::putAll);

      aggregations = updateAfterKeyInCompositeAggregation(safeAfterKeyMap, aggregations);

      return compositeAggregationResult.buckets().array();
    } catch (final RuntimeException e) {
      if (isInstanceIndexNotFoundException(e)) {
        LOG.info(
            "Was not able to get next page of {} aggregation because at least one instance from {} does not exist.",
            pathToAggregation.getLast(),
            indices);
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private HashMap<String, Aggregation> updateAfterKeyInCompositeAggregation(
      final Map<String, String> safeAfterKeyMap, final Map<String, Aggregation> currentAgg) {
    return updateAfterKeyInCompositeAggregation(safeAfterKeyMap, currentAgg, false);
  }

  private HashMap<String, Aggregation> updateAfterKeyInCompositeAggregation(
      final Map<String, String> safeAfterKeyMap,
      final Map<String, Aggregation> currentAgg,
      final boolean isFromNested) {
    final HashMap<String, Aggregation> newAggregations = new HashMap<>();

    if (safeAfterKeyMap.isEmpty()) {
      return new HashMap<>(currentAgg);
    }

    for (final String aggPath : pathToAggregation) {
      final Aggregation agg = currentAgg.get(aggPath);
      if (agg == null) {
        continue;
      }

      if (agg.isNested()) {
        final Aggregation newNestedAgg =
            new Builder()
                .nested(new NestedAggregation.Builder().path(agg.nested().path()).build())
                .aggregations(
                    updateAfterKeyInCompositeAggregation(safeAfterKeyMap, agg.aggregations(), true))
                .build();
        newAggregations.put(aggPath, newNestedAgg);
      } else if (agg.isComposite()) {
        final CompositeAggregation newAgg =
            updateCompositeAggregation(agg.composite(), safeAfterKeyMap);
        if (isFromNested) {
          newAggregations.put(aggPath, newAgg._toAggregation());
        } else {
          final Aggregation upgradeAgg =
              Aggregation.of(
                  a -> {
                    if (agg.aggregations() != null) {
                      a.aggregations(agg.aggregations());
                    }
                    return a.composite(newAgg);
                  });
          newAggregations.put(aggPath, upgradeAgg);
        }
      }
    }
    return newAggregations;
  }

  private CompositeAggregation updateCompositeAggregation(
      final CompositeAggregation prevCompositeAggregation,
      final Map<String, String> safeAfterKeyMap) {
    return new CompositeAggregation.Builder()
        .sources(prevCompositeAggregation.sources())
        .size(prevCompositeAggregation.size())
        .name(prevCompositeAggregation.name())
        .meta(prevCompositeAggregation.meta())
        .after(safeAfterKeyMap)
        .build();
  }

  private CompositeAggregate extractCompositeAggregationResult(
      final SearchResponse searchResponse) {
    Map<String, Aggregate> aggregations = searchResponse.aggregations();
    // find aggregation response
    for (int i = 0; i < pathToAggregation.size() - 1; i++) {
      final Aggregate agg = aggregations.get(pathToAggregation.get(i));
      if (agg.isNested()) {
        aggregations = agg.nested().aggregations();
      }
    }
    return aggregations.get(pathToAggregation.getLast()).composite();
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
  public OpenSearchCompositeAggregationScroller setPathToAggregation(
      final String... pathToAggregation) {
    this.pathToAggregation = new LinkedList<>(Arrays.asList(pathToAggregation));
    return this;
  }
}
