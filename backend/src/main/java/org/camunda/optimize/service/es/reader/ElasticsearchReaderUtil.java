/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.PageResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ElasticsearchReaderUtil {

  public static <T> List<T> retrieveAllScrollResults(final SearchResponse initialScrollResponse,
                                                     final Class<T> itemClass,
                                                     final ObjectMapper objectMapper,
                                                     final OptimizeElasticsearchClient esClient,
                                                     final Integer scrollingTimeoutInSeconds) {
    return retrieveScrollResultsTillLimit(
      initialScrollResponse, itemClass, objectMapper, esClient, scrollingTimeoutInSeconds, Integer.MAX_VALUE
    );
  }

  public static <T> List<T> retrieveAllScrollResults(final SearchResponse initialScrollResponse,
                                                     final Class<T> itemClass,
                                                     final Function<SearchHit, T> mappingFunction,
                                                     final OptimizeElasticsearchClient esClient,
                                                     final Integer scrollingTimeoutInSeconds) {
    return retrieveScrollResultsTillLimit(
      initialScrollResponse, itemClass, mappingFunction, esClient, scrollingTimeoutInSeconds, Integer.MAX_VALUE
    );
  }

  public static <T> List<T> retrieveScrollResultsTillLimit(final SearchResponse initialScrollResponse,
                                                           final Class<T> itemClass,
                                                           final ObjectMapper objectMapper,
                                                           final OptimizeElasticsearchClient esClient,
                                                           final Integer scrollingTimeoutInSeconds,
                                                           final Integer limit) {
    Function<SearchHit, T> mappingFunction = hit -> {
      final String sourceAsString = hit.getSourceAsString();
      try {
        return objectMapper.readValue(sourceAsString, itemClass);
      } catch (IOException e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: " + sourceAsString;
        log.error(reason, itemClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    };
    return retrieveScrollResultsTillLimit(
      initialScrollResponse, itemClass, mappingFunction, esClient, scrollingTimeoutInSeconds, limit
    );
  }

  public static <T> PageResultDto<T> retrieveNextScrollResultsPage(final String scrollId,
                                                                   final Class<T> itemClass,
                                                                   final Function<SearchHit, T> mappingFunction,
                                                                   final OptimizeElasticsearchClient esClient,
                                                                   final Integer scrollingTimeoutInSeconds,
                                                                   final Integer limit) {
    final PageResultDto<T> pageResult = new PageResultDto<>(limit);

    String currentScrollId = scrollId;
    SearchHits currentHits;
    do {
      if (pageResult.getEntities().size() < limit) {
        final SearchResponse currentScrollResp =
          getScrollResponse(esClient, scrollingTimeoutInSeconds, currentScrollId);
        currentScrollId = currentScrollResp.getScrollId();
        currentHits = currentScrollResp.getHits();
        pageResult.getEntities().addAll(
          mapHits(currentHits, limit - pageResult.getEntities().size(), itemClass, mappingFunction)
        );
        pageResult.setPagingState(currentScrollId);
      } else {
        currentHits = null;
      }
    } while (currentHits != null && currentHits.getHits().length > 0);

    if (pageResult.getEntities().isEmpty() || pageResult.getEntities().size() < limit) {
      clearScroll(itemClass, esClient, currentScrollId);
      pageResult.setPagingState(null);
    }

    return pageResult;
  }

  private static SearchResponse getScrollResponse(final OptimizeElasticsearchClient esClient,
                                                  final Integer scrollingTimeoutInSeconds,
                                                  final String scrollId) {
    final SearchResponse currentScrollResp;
    final SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId)
      .scroll(TimeValue.timeValueSeconds(scrollingTimeoutInSeconds));
    try {
      currentScrollResp = esClient.scroll(scrollRequest);
    } catch (IOException e) {
      String reason = String.format("Could not get scroll response through entries for scrollId [%s].", scrollId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return currentScrollResp;
  }

  public static <T> List<T> retrieveScrollResultsTillLimit(final SearchResponse initialScrollResponse,
                                                           final Class<T> itemClass,
                                                           final Function<SearchHit, T> mappingFunction,
                                                           final OptimizeElasticsearchClient esClient,
                                                           final Integer scrollingTimeoutInSeconds,
                                                           final Integer limit) {
    final List<T> results = new ArrayList<>();

    SearchResponse currentScrollResp = initialScrollResponse;
    SearchHits hits = currentScrollResp.getHits();

    while (hits != null && hits.getHits().length > 0) {
      results.addAll(mapHits(hits, limit - results.size(), itemClass, mappingFunction));

      if (results.size() < limit) {
        final SearchScrollRequest scrollRequest = new SearchScrollRequest(currentScrollResp.getScrollId());
        scrollRequest.scroll(TimeValue.timeValueSeconds(scrollingTimeoutInSeconds));
        try {
          currentScrollResp = esClient.scroll(scrollRequest);
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
    clearScroll(itemClass, esClient, currentScrollResp.getScrollId());

    return results;
  }

  private static <T> void clearScroll(final Class<T> itemClass,
                                      final OptimizeElasticsearchClient esClient,
                                      final String scrollId) {
    try {
      ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      ClearScrollResponse clearScrollResponse = esClient.clearScroll(clearScrollRequest);
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
    Function<SearchHit, T> mappingFunction = hit -> {
      final String sourceAsString = hit.getSourceAsString();
      try {
        return objectMapper.readValue(sourceAsString, itemClass);
      } catch (IOException e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: " + sourceAsString;
        log.error(reason, itemClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    };
    return mapHits(searchHits, Integer.MAX_VALUE, itemClass, mappingFunction);
  }

  public static <T> List<T> mapHits(final SearchHits searchHits,
                                    final Integer resultLimit,
                                    final Class<T> itemClass,
                                    final Function<SearchHit, T> mappingFunction) {
    final List<T> results = new ArrayList<>();
    for (SearchHit hit : searchHits) {
      if (results.size() >= resultLimit) {
        break;
      }

      try {
        final T mappedHit = mappingFunction.apply(hit);
        results.add(mappedHit);
      } catch (Exception e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!";
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
