/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.MultiBucketAggregateBase;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.get.GetResult;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;

public class OpensearchReaderUtil {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OpensearchReaderUtil.class);

  public static <T> List<T> extractResponseValues(final SearchResponse<T> searchResponse) {
    return Optional.ofNullable(searchResponse)
        .map(SearchResponse::hits)
        .map(HitsMetadata::hits)
        .map(hits -> hits.stream().map(Hit::source).toList())
        .orElseThrow(
            () -> {
              final String reason = "Was not able to parse response values from OpenSearch";
              LOG.error(reason);
              return new OptimizeRuntimeException(reason);
            });
  }

  public static <T> List<T> extractResponseValues(
      final SearchResponse<T> searchResponse, final Function<Hit<T>, T> mappingFunction) {
    return Optional.ofNullable(searchResponse)
        .map(SearchResponse::hits)
        .map(HitsMetadata::hits)
        .map(hits -> hits.stream().map(mappingFunction).toList())
        .orElseThrow(
            () -> {
              final String reason = "Was not able to parse response values from OpenSearch";
              LOG.error(reason);
              return new OptimizeRuntimeException(reason);
            });
  }

  public static <T> Set<String> extractAggregatedResponseValues(
      final SearchResponse<T> searchResponse, final String aggPath) {
    return Optional.ofNullable(searchResponse)
        .map(response -> response.aggregations().get(aggPath))
        .filter(Aggregate::isSterms)
        .map(Aggregate::sterms)
        .map(MultiBucketAggregateBase::buckets)
        .map(Buckets::array)
        .map(Collection::stream)
        .map(streamBuckets -> streamBuckets.map(StringTermsBucket::key).collect(Collectors.toSet()))
        .orElseThrow(
            () -> {
              final String reason =
                  String.format(
                      "Was not able to parse aggregated sterm response values from OpenSearch with path %s",
                      aggPath);
              LOG.error(reason);
              return new OptimizeRuntimeException(reason);
            });
  }

  public static <T> List<T> extractAggregatedResponseValues(
      final OpenSearchDocumentOperations.AggregatedResult<Hit<T>> searchResponse) {
    return extractAggregatedResponseValues(searchResponse, Hit::source);
  }

  public static <T> List<T> extractAggregatedResponseValues(
      final OpenSearchDocumentOperations.AggregatedResult<Hit<T>> searchResponse,
      final Function<Hit<T>, T> mappingFunction) {
    return Optional.ofNullable(searchResponse)
        .map(OpenSearchDocumentOperations.AggregatedResult::values)
        .map(hits -> hits.stream().map(mappingFunction).collect(Collectors.toList()))
        .orElseThrow(
            () -> {
              final String reason = "Was not able to parse response aggregations from OpenSearch";
              LOG.error(reason);
              return new OptimizeRuntimeException(reason);
            });
  }

  public static <T> Optional<T> processGetResponse(final GetResult<T> getResponse) {
    return Optional.ofNullable(getResponse).filter(GetResult::found).map(GetResult::source);
  }

  public static <T> Collection<? extends T> mapHits(
      final HitsMetadata<JsonData> searchHits,
      final int resultLimit,
      final Class<T> typeClass,
      final Function<Hit<T>, T> mappingFunction) {
    final List<T> results = new ArrayList<>();
    for (final Hit<JsonData> hit : searchHits.hits()) {
      if (results.size() >= resultLimit) {
        break;
      }
      try {
        final Optional<JsonData> optionalMappedHit = Optional.ofNullable(hit.source());
        optionalMappedHit.ifPresent(
            hitValue -> {
              try {
                final T definitionDto = hitValue.to(typeClass);
                final Hit<T> adaptedHit =
                    new Hit.Builder<T>().index(hit.index()).source(definitionDto).build();
                final T enrichedDto = mappingFunction.apply(adaptedHit);
                results.add(enrichedDto);
              } catch (final Exception e) {
                final String reason =
                    "While mapping search results to class {} "
                        + "it was not possible to deserialize a hit from OpenSearch!";
                LOG.error(reason, typeClass.getSimpleName(), e);
                throw new OptimizeRuntimeException(reason);
              }
            });
      } catch (final Exception e) {
        final String reason =
            "While mapping search results to class {} "
                + "it was not possible to deserialize a hit from Opensearch!";
        LOG.error(reason, typeClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return results;
  }
}
