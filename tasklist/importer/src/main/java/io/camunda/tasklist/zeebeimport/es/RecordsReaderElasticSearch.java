/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.*;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NoSuchIndexException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.RecordsReaderAbstract;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Represents Zeebe data reader for one partition and one value type. After reading the data is also
 * schedules the jobs for import execution. Each reader can have it's own backoff, so that we make a
 * pause in case there is no data currently for given partition and value type.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
@Conditional(ElasticSearchCondition.class)
public class RecordsReaderElasticSearch extends RecordsReaderAbstract {

  private static final Logger LOGGER = LoggerFactory.getLogger(RecordsReaderElasticSearch.class);

  @Autowired
  @Qualifier("tasklistZeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  public RecordsReaderElasticSearch(
      final int partitionId, final ImportValueType importValueType, final int queueSize) {
    super(partitionId, importValueType, queueSize);
  }

  private <A> A withTimer(final Callable<A> callable) throws Exception {
    return metrics
        .getTimer(
            Metrics.TIMER_NAME_IMPORT_QUERY,
            Metrics.TAG_KEY_TYPE,
            importValueType.name(),
            Metrics.TAG_KEY_PARTITION,
            String.valueOf(partitionId))
        .recordCallable(callable);
  }

  private ImportBatchElasticSearch createImportBatch(final SearchResponse searchResponse) {
    final SearchHit[] hits = searchResponse.getHits().getHits();
    String indexName = null;
    if (hits.length > 0) {
      indexName = hits[hits.length - 1].getIndex();
    }
    return new ImportBatchElasticSearch(
        partitionId, importValueType, Arrays.asList(hits), indexName);
  }

  private ImportBatchElasticSearch createImportBatch(final SearchHit[] hits) {
    String indexName = null;
    if (hits.length > 0) {
      indexName = hits[hits.length - 1].getIndex();
    }
    return new ImportBatchElasticSearch(
        partitionId, importValueType, Arrays.asList(hits), indexName);
  }

  private SearchHit[] read(final SearchRequest searchRequest, final boolean scrollNeeded)
      throws IOException {
    String scrollId = null;
    try {
      final List<SearchHit> searchHits = new ArrayList<>();

      if (scrollNeeded) {
        searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
      }
      SearchResponse response = zeebeEsClient.search(searchRequest, RequestOptions.DEFAULT);

      searchHits.addAll(List.of(response.getHits().getHits()));

      if (scrollNeeded) {
        scrollId = response.getScrollId();
        do {
          final SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
          scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

          response = zeebeEsClient.scroll(scrollRequest, RequestOptions.DEFAULT);

          scrollId = response.getScrollId();
          searchHits.addAll(List.of(response.getHits().getHits()));
        } while (response.getHits().getHits().length != 0);
      }
      return searchHits.toArray(new SearchHit[0]);
    } finally {
      if (scrollId != null) {
        clearScroll(scrollId, zeebeEsClient);
      }
    }
  }

  @Override
  public ImportBatch readNextBatchByPositionAndPartition(
      final long positionFrom, final Long positionTo) throws NoSuchIndexException {
    final String aliasName =
        importValueType.getAliasName(tasklistProperties.getZeebeElasticsearch().getPrefix());
    try {

      final SearchRequest searchRequest = createSearchQuery(aliasName, positionFrom, positionTo);

      final SearchResponse searchResponse =
          withTimer(() -> zeebeEsClient.search(searchRequest, RequestOptions.DEFAULT));

      return createImportBatch(searchResponse);

    } catch (final ElasticsearchStatusException ex) {
      if (ex.getMessage().contains("no such index")) {
        LOGGER.debug("No index found for alias {}", aliasName);
        throw new NoSuchIndexException();
      } else {
        final String message =
            String.format(
                "Exception occurred for alias [%s], while obtaining next Zeebe records batch: %s",
                aliasName, ex.getMessage());
        throw new TasklistRuntimeException(message, ex);
      }
    } catch (final Exception e) {
      final String message =
          String.format(
              "Exception occurred for alias [%s], while obtaining next Zeebe records batch: %s",
              aliasName, e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public ImportBatch readNextBatchBySequence(final Long fromSequence, final Long toSequence)
      throws NoSuchIndexException {
    final String aliasName =
        importValueType.getAliasName(tasklistProperties.getZeebeElasticsearch().getPrefix());
    final int batchSize = tasklistProperties.getZeebeElasticsearch().getBatchSize();
    final long lessThanEqualsSequence;
    final int maxNumberOfHits;

    if (toSequence != null && toSequence > 0) {
      // in worst case all the records are duplicated
      maxNumberOfHits = (int) ((toSequence - fromSequence) * 2);
      lessThanEqualsSequence = toSequence;
      LOGGER.debug(
          "Import batch reread was called. Data type {}, partitionId {}, sequence {}, lastSequence {}, maxNumberOfHits {}.",
          importValueType,
          partitionId,
          fromSequence,
          toSequence,
          maxNumberOfHits);
    } else {
      maxNumberOfHits = batchSize;
      if (countEmptyRuns == tasklistProperties.getImporter().getMaxEmptyRuns()) {
        lessThanEqualsSequence = maxPossibleSequence;
        countEmptyRuns = 0;
        LOGGER.debug(
            "Max empty runs reached. Data type {}, partitionId {}, sequence {}, lastSequence {}, maxNumberOfHits {}.",
            importValueType,
            partitionId,
            fromSequence,
            lessThanEqualsSequence,
            maxNumberOfHits);
      } else {
        lessThanEqualsSequence = fromSequence + batchSize;
      }
    }

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .sort(TasklistImportPositionIndex.SEQUENCE, SortOrder.ASC)
            .query(
                rangeQuery(TasklistImportPositionIndex.SEQUENCE)
                    .gt(fromSequence)
                    .lte(lessThanEqualsSequence))
            .size(maxNumberOfHits >= QUERY_MAX_SIZE ? QUERY_MAX_SIZE : maxNumberOfHits);

    final SearchRequest searchRequest =
        new SearchRequest(aliasName)
            .source(searchSourceBuilder)
            .routing(String.valueOf(partitionId))
            .requestCache(false);

    try {
      final SearchHit[] hits =
          withTimer(() -> read(searchRequest, maxNumberOfHits >= QUERY_MAX_SIZE));
      if (hits.length == 0) {
        countEmptyRuns++;
      } else {
        countEmptyRuns = 0;
      }
      return createImportBatch(hits);
    } catch (final ElasticsearchStatusException ex) {
      if (ex.getMessage().contains("no such index")) {
        throw new NoSuchIndexException();
      } else {
        final String message =
            String.format(
                "Exception occurred, while obtaining next Zeebe records batch: %s",
                ex.getMessage());
        throw new TasklistRuntimeException(message, ex);
      }
    } catch (final Exception e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining next Zeebe records batch: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private SearchRequest createSearchQuery(
      final String aliasName, final long positionFrom, final Long positionTo) {
    RangeQueryBuilder positionQ = rangeQuery(TasklistImportPositionIndex.POSITION).gt(positionFrom);
    if (positionTo != null) {
      positionQ = positionQ.lte(positionTo);
    }
    final QueryBuilder queryBuilder =
        joinWithAnd(positionQ, termQuery(PARTITION_ID_FIELD_NAME, partitionId));

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(queryBuilder)
            .sort(TasklistImportPositionIndex.POSITION, SortOrder.ASC);
    if (positionTo == null) {
      searchSourceBuilder =
          searchSourceBuilder.size(tasklistProperties.getZeebeElasticsearch().getBatchSize());
    } else {
      LOGGER.debug(
          "Import batch reread was called. Data type {}, partitionId {}, positionFrom {}, positionTo {}.",
          importValueType,
          partitionId,
          positionFrom,
          positionTo);
      final int size = (int) (positionTo - positionFrom);
      searchSourceBuilder =
          searchSourceBuilder.size(
              size <= 0 || size > QUERY_MAX_SIZE
                  ? QUERY_MAX_SIZE
                  : size); // this size will be bigger than needed
    }
    return new SearchRequest(aliasName)
        .source(searchSourceBuilder)
        .routing(String.valueOf(partitionId))
        .requestCache(false);
  }
}
