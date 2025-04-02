/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.fetcher.es;

import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import io.camunda.optimize.service.importing.zeebe.fetcher.AbstractZeebeRecordFetcher;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;

@Slf4j
@Conditional(ElasticSearchCondition.class)
public abstract class AbstractZeebeRecordFetcherES<T> extends AbstractZeebeRecordFetcher<T> {

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
    if (e instanceof ElasticsearchException) {
      return e.getMessage().contains(INDEX_NOT_FOUND_EXCEPTION_TYPE);
    }
    return false;
  }

  @Override
  protected List<T> fetchZeebeRecordsForPrefixAndPartitionFrom(
      final PositionBasedImportPage positionBasedImportPage) throws Exception {
    final SearchResponse<T> searchResponse =
        esClient.searchWithoutPrefixing(
            SearchRequest.of(
                s ->
                    s.index(getIndexAlias())
                        .query(getRecordQuery(positionBasedImportPage))
                        .size(getDynamicBatchSize())
                        .sort(
                            ss ->
                                ss.field(
                                    f ->
                                        f.field(getSortField(positionBasedImportPage))
                                            .order(SortOrder.Asc)))
                        .routing(String.valueOf(partitionId))
                        .requestCache(false)),
            getRecordDtoClass());
    if (!searchResponse.shards().failures().isEmpty()
        || (searchResponse.shards().total().intValue()
            > (searchResponse.shards().failed().intValue()
                + searchResponse.shards().successful().intValue()))) {
      throw new OptimizeRuntimeException("Not all shards could be searched successfully");
    }
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), getRecordDtoClass(), objectMapper);
  }

  private Query getRecordQuery(final PositionBasedImportPage positionBasedImportPage) {
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
        CountRequest.of(
            c ->
                c.index(getIndexAlias())
                    .query(buildPositionQuery(positionBasedImportPage))
                    .routing(String.valueOf(partitionId)));
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

  private Query buildPositionQuery(final PositionBasedImportPage positionBasedImportPage) {
    log.trace(
        "using position query for records of {} on partition {}",
        getBaseIndexName(),
        getPartitionId());
    return Query.of(
        q ->
            q.bool(
                m ->
                    m.must(
                            u ->
                                u.range(
                                    r ->
                                        r.number(
                                            n ->
                                                n.field(ZeebeRecordDto.Fields.position)
                                                    .gt(
                                                        Double.valueOf(
                                                            positionBasedImportPage
                                                                .getPosition())))))
                        .must(
                            u ->
                                u.term(
                                    r ->
                                        r.field(ZeebeRecordDto.Fields.partitionId)
                                            .value(partitionId)))));
  }

  private Query buildSequenceQuery(final PositionBasedImportPage positionBasedImportPage) {
    log.trace(
        "using sequence query for records of {} on partition {}",
        getBaseIndexName(),
        getPartitionId());
    return Query.of(
        q ->
            q.bool(
                m ->
                    m.must(
                        u ->
                            u.range(
                                r ->
                                    r.number(
                                        n ->
                                            n.field(ZeebeRecordDto.Fields.sequence)
                                                .gt(
                                                    Double.valueOf(
                                                        positionBasedImportPage.getSequence()))
                                                .lte(
                                                    (double)
                                                        (positionBasedImportPage.getSequence()
                                                            + getDynamicBatchSize())))))));
  }
}
