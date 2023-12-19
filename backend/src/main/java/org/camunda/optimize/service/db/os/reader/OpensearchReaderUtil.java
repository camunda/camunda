/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.get.GetResult;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

import java.util.List;
import java.util.Optional;

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

}
