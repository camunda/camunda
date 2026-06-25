/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.PageResultDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;

public final class ElasticsearchReaderUtil {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ElasticsearchReaderUtil.class);

  private ElasticsearchReaderUtil() {}

  public static <T> List<T> retrieveAllScrollResults(
      final SearchResponse<T> initialScrollResponse,
      final Class<T> itemClass,
      final ObjectMapper objectMapper,
      final OptimizeElasticsearchClient esClient,
      final Integer scrollingTimeoutInSeconds) {
    return retrieveScrollResultsTillLimit(
        initialScrollResponse,
        itemClass,
        objectMapper,
        esClient,
        scrollingTimeoutInSeconds,
        Integer.MAX_VALUE);
  }

  public static <T> List<T> retrieveScrollResultsTillLimit(
      final ResponseBody<?> initialScrollResponse,
      final Class<T> itemClass,
      final ObjectMapper objectMapper,
      final OptimizeElasticsearchClient esClient,
      final Integer scrollingTimeoutInSeconds,
      final Integer limit) {
    return retrieveScrollResultsTillLimit(
        initialScrollResponse,
        itemClass,
        objectMapper,
        esClient,
        scrollingTimeoutInSeconds,
        limit,
        false);
  }

  public static <T> List<T> retrieveScrollResultsTillLimit(
      final ResponseBody<?> initialScrollResponse,
      final Class<T> itemClass,
      final ObjectMapper objectMapper,
      final OptimizeElasticsearchClient esClient,
      final Integer scrollingTimeoutInSeconds,
      final Integer limit,
      final boolean agg) {
    return retrieveScrollResultsTillLimit(
        initialScrollResponse,
        itemClass,
        h -> {
          if (agg) {
            try {
              return objectMapper.readValue(h.source().toString(), itemClass);
            } catch (final JsonProcessingException e) {
              throw new RuntimeException(e);
            }
          } else {
            return objectMapper.convertValue(h.source(), itemClass);
          }
        },
        esClient,
        scrollingTimeoutInSeconds,
        limit);
  }

  public static <T> PageResultDto<T> retrieveNextScrollResultsPage(
      final String scrollId,
      final Class<T> itemClass,
      final Function<Hit<?>, T> mappingFunction,
      final OptimizeElasticsearchClient esClient,
      final Integer scrollingTimeoutInSeconds,
      final Integer limit) {
    final PageResultDto<T> pageResult = new PageResultDto<>(limit);

    String currentScrollId = scrollId;
    HitsMetadata<?> currentHits;
    do {
      if (pageResult.getEntities().size() < limit) {
        final ScrollResponse<?> currentScrollResp =
            getScrollResponse(esClient, scrollingTimeoutInSeconds, currentScrollId);
        currentScrollId = currentScrollResp.scrollId();
        currentHits = currentScrollResp.hits();
        pageResult
            .getEntities()
            .addAll(
                mapHits(
                    currentHits,
                    limit - pageResult.getEntities().size(),
                    itemClass,
                    mappingFunction));
        pageResult.setPagingState(currentScrollId);
      } else {
        currentHits = null;
      }
    } while (currentHits != null && !currentHits.hits().isEmpty());

    if (pageResult.getEntities().isEmpty() || pageResult.getEntities().size() < limit) {
      clearScroll(itemClass, esClient, currentScrollId);
      pageResult.setPagingState(null);
    }

    return pageResult;
  }

  private static ScrollResponse<?> getScrollResponse(
      final OptimizeElasticsearchClient esClient,
      final Integer scrollingTimeoutInSeconds,
      final String scrollId) {
    final ScrollResponse<?> currentScrollResp;
    final ScrollRequest scrollRequest =
        ScrollRequest.of(
            b -> b.scrollId(scrollId).scroll(s -> s.time(scrollingTimeoutInSeconds + "s")));
    try {
      currentScrollResp = esClient.scroll(scrollRequest, Object.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Could not get scroll response through entries for scrollId [%s].", scrollId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return currentScrollResp;
  }

  public static <T> List<T> retrieveScrollResultsTillLimit(
      final ResponseBody<?> initialScrollResponse,
      final Class<T> itemClass,
      final Function<Hit<?>, T> mappingFunction,
      final OptimizeElasticsearchClient esClient,
      final Integer scrollingTimeoutInSeconds,
      final Integer limit) {
    final List<T> results = new ArrayList<>();

    ResponseBody<?> currentScrollResp = initialScrollResponse;
    HitsMetadata<?> hits = currentScrollResp.hits();

    while (hits != null && !hits.hits().isEmpty()) {
      results.addAll(mapHits(hits, limit - results.size(), itemClass, mappingFunction));

      if (results.size() < limit) {
        try {
          final ResponseBody<?> finalCurrentScrollResp = currentScrollResp;
          currentScrollResp =
              esClient.scroll(
                  ScrollRequest.of(
                      s ->
                          s.scrollId(finalCurrentScrollResp.scrollId())
                              .scroll(o -> o.time(scrollingTimeoutInSeconds + "s"))),
                  itemClass);
          hits = currentScrollResp.hits();
        } catch (final IOException e) {
          final String reason =
              String.format(
                  "Could not scroll through entries for class [%s].", itemClass.getSimpleName());
          LOG.error(reason, e);
          throw new OptimizeRuntimeException(reason, e);
        }
      } else {
        hits = null;
      }
    }
    clearScroll(itemClass, esClient, currentScrollResp.scrollId());

    return results;
  }

  private static <T> void clearScroll(
      final Class<T> itemClass, final OptimizeElasticsearchClient esClient, final String scrollId) {
    final ClearScrollResponse clearScrollResponse =
        esClient.clearScroll(ClearScrollRequest.of(b -> b.scrollId(scrollId)));
    final boolean succeeded = clearScrollResponse.succeeded();
    if (!succeeded) {
      final String reason =
          String.format(
              "Could not clear scroll for class [%s], since Elasticsearch was unable to perform the action!",
              itemClass.getSimpleName());
      LOG.error(reason);
    }
  }

  /*
   Returns an iterator to extract all results from a given search (using search_after instead of scrolls).

   This is done in a lazy fashion so we do not read everything into memory at once.
  */
  public static <T> Iterator<List<T>> searchIterator(
      final OptimizeElasticsearchClient esClient,
      final SearchRequest searchRequest,
      final Class<T> itemClass) {
    return new SearchAfterIterator<>(esClient, searchRequest, itemClass);
  }

  public static <T> List<T> mapHits(
      final HitsMetadata<?> searchHits, final Class<T> itemClass, final ObjectMapper objectMapper) {
    return mapHits(searchHits, itemClass, objectMapper, false);
  }

  public static <T> List<T> mapHits(
      final HitsMetadata<?> searchHits,
      final Class<T> itemClass,
      final ObjectMapper objectMapper,
      final boolean agg) {
    return mapHits(
        searchHits,
        Integer.MAX_VALUE,
        itemClass,
        h -> {
          if (itemClass.isInstance(h.source())) {
            return itemClass.cast(h.source());
          } else {
            if (agg) {
              try {
                return objectMapper.readValue(h.source().toString(), itemClass);
              } catch (final JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            } else {
              return objectMapper.convertValue(h.source(), itemClass);
            }
          }
        });
  }

  public static <T> List<T> mapHits(
      final HitsMetadata<?> searchHits,
      final Integer resultLimit,
      final Class<T> itemClass,
      final Function<Hit<?>, T> mappingFunction) {
    final List<T> results = new ArrayList<>();
    for (final Hit<?> hit : searchHits.hits()) {
      if (results.size() >= resultLimit) {
        break;
      }

      try {
        final T mappedHit = mappingFunction.apply(hit);
        results.add(mappedHit);
      } catch (final Exception e) {
        final String reason =
            "While mapping search results to class {} "
                + "it was not possible to deserialize a hit from Elasticsearch!";
        LOG.error(reason, itemClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return results;
  }

  public static <T> boolean atLeastOneResponseExistsForMultiGet(
      final MgetResponse<T> multiGetResponse) {
    return multiGetResponse.docs().stream()
        .anyMatch(multiGetItemResponse -> multiGetItemResponse.result().found());
  }

  // essentially backported rebuild method from SearchRequest in later versions of ES client
  public static SearchRequest.Builder rebuild(final SearchRequest instance) {
    return new SearchRequest.Builder()
        .source(instance.source())
        .aggregations(instance.aggregations())
        .allowNoIndices(instance.allowNoIndices())
        .allowPartialSearchResults(instance.allowPartialSearchResults())
        .analyzeWildcard(instance.analyzeWildcard())
        .analyzer(instance.analyzer())
        .batchedReduceSize(instance.batchedReduceSize())
        .ccsMinimizeRoundtrips(instance.ccsMinimizeRoundtrips())
        .collapse(instance.collapse())
        .defaultOperator(instance.defaultOperator())
        .df(instance.df())
        .docvalueFields(instance.docvalueFields())
        .expandWildcards(instance.expandWildcards())
        .explain(instance.explain())
        .ext(instance.ext())
        .fields(instance.fields())
        .forceSyntheticSource(instance.forceSyntheticSource())
        .from(instance.from())
        .highlight(instance.highlight())
        .ignoreThrottled(instance.ignoreThrottled())
        .ignoreUnavailable(instance.ignoreUnavailable())
        .index(instance.index())
        .indicesBoost(instance.indicesBoost())
        .knn(instance.knn())
        .lenient(instance.lenient())
        .maxConcurrentShardRequests(instance.maxConcurrentShardRequests())
        .minCompatibleShardNode(instance.minCompatibleShardNode())
        .minScore(instance.minScore())
        .pit(instance.pit())
        .postFilter(instance.postFilter())
        .preFilterShardSize(instance.preFilterShardSize())
        .preference(instance.preference())
        .profile(instance.profile())
        .q(instance.q())
        .query(instance.query())
        .rank(instance.rank())
        .requestCache(instance.requestCache())
        .rescore(instance.rescore())
        .retriever(instance.retriever())
        .routing(instance.routing())
        .runtimeMappings(instance.runtimeMappings())
        .scriptFields(instance.scriptFields())
        .scroll(instance.scroll())
        .searchAfter(instance.searchAfter())
        .searchType(instance.searchType())
        .seqNoPrimaryTerm(instance.seqNoPrimaryTerm())
        .size(instance.size())
        .slice(instance.slice())
        .sort(instance.sort())
        .stats(instance.stats())
        .storedFields(instance.storedFields())
        .suggest(instance.suggest())
        .terminateAfter(instance.terminateAfter())
        .timeout(instance.timeout())
        .trackScores(instance.trackScores())
        .trackTotalHits(instance.trackTotalHits())
        .version(instance.version());
  }
}
