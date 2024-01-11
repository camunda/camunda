/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.search.SearchHit;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.get.GetResult;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class OpensearchReaderUtil {

  public static <T> List<T> extractResponseValues(final SearchResponse<T> searchResponse) {
    return Optional.ofNullable(searchResponse)
      .map(SearchResponse::hits)
      .map(HitsMetadata::hits)
      .map(hits -> hits.stream().map(Hit::source).toList())
      .orElseThrow(() -> {
        String reason = "Was not able to parse response values from OpenSearch";
        log.error(reason);
        return new OptimizeRuntimeException(reason);
      });
  }

  public static <T> List<T> extractAggregatedResponseValues(final OpenSearchDocumentOperations.AggregatedResult<Hit<T>> searchResponse) {
    return Optional.ofNullable(searchResponse)
      .map(OpenSearchDocumentOperations.AggregatedResult::values)
      .map(hits -> hits.stream().map(Hit::source).toList())
      .orElseThrow(() -> {
        String reason = "Was not able to parse response aggregations from OpenSearch";
        log.error(reason);
        return new OptimizeRuntimeException(reason);
      });
  }

  public static <T> Optional<T> processGetResponse(GetResult<T> getResponse) {
    return Optional.ofNullable(getResponse)
      .filter(GetResult::found)
      .map(GetResult::source);
  }

  public static <T> Collection<? extends T> mapHits(final HitsMetadata<JsonData> searchHits,
                                                    final int resultLimit, final Class<T> typeClass) {
    final List<T> results = new ArrayList<>();
    for (Hit<JsonData> hit : searchHits.hits()) {
      if (results.size() >= resultLimit) {
        break;
      }

      try {
        final Optional<JsonData> optionalMappedHit = Optional.ofNullable(hit.source());
        optionalMappedHit.ifPresent(hitValue -> results.add(hitValue.to(typeClass)));
      } catch (Exception e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Opensearch!";
        log.error(reason, typeClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return results;
  }
}
