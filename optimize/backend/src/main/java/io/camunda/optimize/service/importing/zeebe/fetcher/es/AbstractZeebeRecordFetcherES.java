/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.fetcher.es;

import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import io.camunda.optimize.service.importing.zeebe.fetcher.AbstractZeebeRecordFetcher;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Arrays;
import java.util.List;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticSearchCondition.class)
public abstract class AbstractZeebeRecordFetcherES<T> extends AbstractZeebeRecordFetcher<T> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(AbstractZeebeRecordFetcherES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  protected AbstractZeebeRecordFetcherES(
      final int partitionId,
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService) {
    super(partitionId, configurationService);
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean isZeebeInstanceIndexNotFoundException(final Exception e) {
    if (e instanceof ElasticsearchStatusException) {
      return Arrays.stream(e.getSuppressed())
          .map(Throwable::getMessage)
          .anyMatch(msg -> msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE));
    }
    return false;
  }

  @Override
  protected List<T> fetchZeebeRecordsForPrefixAndPartitionFrom(
      final PositionBasedImportPage positionBasedImportPage) throws Exception {

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(getRecordQuery(positionBasedImportPage))
            .size(getDynamicBatchSize())
            .sort(getSortField(positionBasedImportPage), SortOrder.ASC);
    final SearchRequest searchRequest =
        new SearchRequest(getIndexAlias())
            .source(searchSourceBuilder)
            .routing(String.valueOf(partitionId))
            .requestCache(false);

    final SearchResponse searchResponse = esClient.searchWithoutPrefixing(searchRequest);
    if (searchResponse.getFailedShards() > 0
        || (searchResponse.getTotalShards()
            > (searchResponse.getFailedShards() + searchResponse.getSuccessfulShards()))) {
      throw new OptimizeRuntimeException("Not all shards could be searched successfully");
    }
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), getRecordDtoClass(), objectMapper);
  }

  private BoolQueryBuilder getRecordQuery(final PositionBasedImportPage positionBasedImportPage) {
    // We use the position query if no record with sequences have been imported yet, or if we know
    // that there is data to be
    // imported that Optimize is not catching in its sequence query. This can happen in the event
    // that the next page of
    // records no longer exist and the next record to import will have a sequence greater than the
    // max range of the sequence query
    if (!positionBasedImportPage.isHasSeenSequenceField()
        || nextSequenceRecordIsBeyondSequenceQuery(positionBasedImportPage)) {
      return buildPositionQuery(positionBasedImportPage);
    } else {
      return buildSequenceQuery(positionBasedImportPage);
    }
  }

  private boolean nextSequenceRecordIsBeyondSequenceQuery(
      final PositionBasedImportPage positionBasedImportPage) {
    // We only check for new data beyond the upper sequence range if the max configured number of
    // empty pages has been reached
    if (getConsecutiveEmptyPages() < getZeebeImportConfig().getMaxEmptyPagesToImport()) {
      return false;
    }
    final CountRequest countRequest =
        new CountRequest(getIndexAlias())
            .query(buildPositionQuery(positionBasedImportPage))
            .routing(String.valueOf(partitionId));
    try {
      log.info(
          "Using the position query to see if there are new records in the {} index on partition {}",
          getBaseIndexName(),
          partitionId);
      final long numberOfRecordsFound = esClient.countWithoutPrefix(countRequest);
      if (numberOfRecordsFound > 0) {
        log.info(
            "Found {} records in index {} on partition {} that can't be imported by the current sequence query. Will revert to "
                + "position query for the next fetch attempt",
            numberOfRecordsFound,
            getBaseIndexName(),
            partitionId);
        return true;
      } else {
        log.info(
            "There are no newer records to process, so empty pages of records are currently expected");
      }
    } catch (final Exception e) {
      if (isZeebeInstanceIndexNotFoundException(e)) {
        log.warn("No Zeebe index of type {} found to count records from!", getIndexAlias());
      } else {
        log.warn(
            "There was an error when looking for records to import beyond the boundaries of the sequence request"
                + e);
      }
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    return false;
  }

  private BoolQueryBuilder buildPositionQuery(
      final PositionBasedImportPage positionBasedImportPage) {
    log.trace(
        "using position query for records of {} on partition {}",
        getBaseIndexName(),
        getPartitionId());
    return boolQuery()
        .must(termQuery(ZeebeRecordDto.Fields.partitionId, partitionId))
        .must(rangeQuery(ZeebeRecordDto.Fields.position).gt(positionBasedImportPage.getPosition()));
  }

  private BoolQueryBuilder buildSequenceQuery(
      final PositionBasedImportPage positionBasedImportPage) {
    log.trace(
        "using sequence query for records of {} on partition {}",
        getBaseIndexName(),
        getPartitionId());
    return boolQuery()
        .must(
            rangeQuery(ZeebeRecordDto.Fields.sequence)
                .gt(positionBasedImportPage.getSequence())
                .lte(positionBasedImportPage.getSequence() + getDynamicBatchSize()));
  }
}
