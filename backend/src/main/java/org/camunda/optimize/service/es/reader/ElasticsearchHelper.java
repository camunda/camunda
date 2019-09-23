/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@UtilityClass
public class ElasticsearchHelper {

  public static <T> List<T> retrieveAllScrollResults(final SearchResponse initialScrollResponse,
                                                     final Class<T> itemClass,
                                                     final ObjectMapper objectMapper,
                                                     final OptimizeElasticsearchClient esclient,
                                                     final Integer scrollingTimeout) {
    return retrieveScrollResultsTillLimit(
      initialScrollResponse, itemClass, objectMapper, esclient, scrollingTimeout, Integer.MAX_VALUE
    );
  }

  public static <T> List<T> retrieveScrollResultsTillLimit(final SearchResponse initialScrollResponse,
                                                           final Class<T> itemClass,
                                                           final ObjectMapper objectMapper,
                                                           final OptimizeElasticsearchClient esclient,
                                                           final Integer scrollingTimeout,
                                                           final Integer limit) {
    final List<T> results = new ArrayList<>();

    SearchResponse currentScrollResp = initialScrollResponse;
    SearchHits hits = currentScrollResp.getHits();

    while (hits != null && hits.getHits().length > 0) {
      results.addAll(mapHits(hits, limit - results.size(), itemClass, objectMapper));

      if (hits.getTotalHits() > results.size() && results.size() < limit) {
        final SearchScrollRequest scrollRequest = new SearchScrollRequest(currentScrollResp.getScrollId());
        scrollRequest.scroll(TimeValue.timeValueSeconds(scrollingTimeout));
        try {
          currentScrollResp = esclient.scroll(scrollRequest, RequestOptions.DEFAULT);
          hits = currentScrollResp.getHits();
        } catch (IOException e) {
          String reason = String.format(
            "Could not scroll through entries for class [%s].",
            itemClass.getSimpleName()
          );
          log.error(reason, e);
          throw new OptimizeRuntimeException(reason, e);
        }
      } else {
        hits = null;
      }
    }
    clearScroll(itemClass, esclient, currentScrollResp.getScrollId());

    return results;
  }

  public static <T> void clearScroll(final Class<T> itemClass, final OptimizeElasticsearchClient esclient,
                                      final String scrollId) {
    try {
      ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      ClearScrollResponse clearScrollResponse = esclient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
      boolean succeeded = clearScrollResponse.isSucceeded();
      if (!succeeded) {
        String reason = String.format(
          "Could not clear scroll for class [%s], since Elasticsearch was unable to perform the action!",
          itemClass.getSimpleName()
        );
        log.error(reason);
      }
    } catch (IOException e) {
      String reason = String.format(
        "Could not close scroll for class [%s].",
        itemClass.getSimpleName()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  public static <T> List<T> mapHits(final SearchHits searchHits,
                                    final Class<T> itemClass,
                                    final ObjectMapper objectMapper) {
    return mapHits(searchHits, Integer.MAX_VALUE, itemClass, objectMapper);
  }

  public static <T> List<T> mapHits(final SearchHits searchHits,
                                    final Integer resultLimit,
                                    final Class<T> itemClass,
                                    final ObjectMapper objectMapper) {
    final List<T> results = new ArrayList<>();
    for (SearchHit hit : searchHits) {
      if (results.size() >= resultLimit) {
        break;
      }

      final String responseAsString = hit.getSourceAsString();
      try {
        final T report = objectMapper.readValue(responseAsString, itemClass);
        results.add(report);
      } catch (IOException e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: "
          + responseAsString;
        log.error(reason, itemClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return results;
  }

  public static boolean atLeastOneResponseExistsForMultiGet(MultiGetResponse multiGetResponse) {
    return Arrays.stream(multiGetResponse.getResponses())
      .anyMatch(multiGetItemResponse -> multiGetItemResponse.getResponse().isExists());
  }
}
