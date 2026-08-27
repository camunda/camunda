/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.JOB_REGISTRY_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.schema.index.JobRegistryIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class JobRegistryReaderES implements JobRegistryReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobRegistryReaderES.class);
  private final OptimizeElasticsearchClient esClient;

  public JobRegistryReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public List<JobRegistryEntryDto> findOldestQueuedJobs(final int limit) {
    LOG.debug("Fetching up to [{}] oldest QUEUED job registry entries.", limit);
    final Query query =
        Query.of(q -> q.term(t -> t.field(JobRegistryIndex.STATUS).value(JobStatus.QUEUED.name())));

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, JOB_REGISTRY_INDEX_NAME)
                    .query(query)
                    .sort(
                        sort ->
                            sort.field(
                                f -> f.field(JobRegistryIndex.CREATED_AT).order(SortOrder.Asc)))
                    // tiebreaker
                    .sort(
                        sort -> sort.field(f -> f.field(JobRegistryIndex.ID).order(SortOrder.Asc)))
                    .size(limit));

    try {
      final SearchResponse<JobRegistryEntryDto> searchResponse =
          esClient.search(searchRequest, JobRegistryEntryDto.class);
      return searchResponse.hits().hits().stream()
          .map(Hit::source)
          .filter(Objects::nonNull)
          .toList();
    } catch (final IOException e) {
      final String message =
          String.format(
              "Was not able to fetch oldest QUEUED job registry entries (limit [%s]).", limit);
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public Optional<JobRegistryEntryDto> findLastByJobTypeAndEntityId(
      final JobType jobType, final EntityType entityType, final String entityId) {
    LOG.debug(
        "Fetching job registry entry for [{}] entity type [{}] entity [{}].",
        jobType,
        entityType,
        entityId);
    final Query query =
        Query.of(
            q ->
                q.bool(
                    jobTypeAndEntityTypeQuery(jobType, entityType)
                        .must(m -> m.term(t -> t.field(JobRegistryIndex.ENTITY_ID).value(entityId)))
                        .build()));

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, JOB_REGISTRY_INDEX_NAME)
                    .query(query)
                    .sort(
                        sort ->
                            sort.field(
                                f -> f.field(JobRegistryIndex.CREATED_AT).order(SortOrder.Desc)))
                    .size(1));

    try {
      final SearchResponse<JobRegistryEntryDto> searchResponse =
          esClient.search(searchRequest, JobRegistryEntryDto.class);

      if (searchResponse.hits().hits().isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable(searchResponse.hits().hits().getFirst().source());
    } catch (final IOException e) {
      final String message =
          String.format(
              "Was not able to fetch job registry entry for [%s] entity type [%s] entity [%s].",
              jobType, entityType, entityId);
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public List<String> findNewestEntityIds(
      final JobType jobType, final EntityType entityType, final int limit) {
    LOG.debug(
        "Fetching up to [{}] newest job registry entityIds for [{}] entity type [{}].",
        limit,
        jobType,
        entityType);

    final BoolQuery.Builder boolQueryBuilder = jobTypeAndEntityTypeQuery(jobType, entityType);

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, JOB_REGISTRY_INDEX_NAME)
                    .query(Query.of(q -> q.bool(boolQueryBuilder.build())))
                    .source(src -> src.filter(f -> f.includes(JobRegistryIndex.ENTITY_ID)))
                    .sort(
                        sort ->
                            sort.field(
                                f -> f.field(JobRegistryIndex.CREATED_AT).order(SortOrder.Desc)))
                    // tiebreaker
                    .sort(
                        sort -> sort.field(f -> f.field(JobRegistryIndex.ID).order(SortOrder.Desc)))
                    // a retried deletion can leave more than one entry for the same entityId;
                    // collapse so a duplicate doesn't consume a slot that another id needs
                    .collapse(c -> c.field(JobRegistryIndex.ENTITY_ID))
                    .size(limit));

    try {
      final SearchResponse<JobRegistryEntryDto> searchResponse =
          esClient.search(searchRequest, JobRegistryEntryDto.class);
      return searchResponse.hits().hits().stream()
          .map(Hit::source)
          .filter(Objects::nonNull)
          .map(JobRegistryEntryDto::getEntityId)
          .toList();
    } catch (final IOException e) {
      final String message =
          String.format(
              "Was not able to fetch newest job registry entityIds for [%s] entity type [%s].",
              jobType, entityType);
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private BoolQuery.Builder jobTypeAndEntityTypeQuery(
      final JobType jobType, final EntityType entityType) {
    return new BoolQuery.Builder()
        .must(m -> m.term(t -> t.field(JobRegistryIndex.JOB_TYPE).value(jobType.name())))
        .must(m -> m.term(t -> t.field(JobRegistryIndex.ENTITY_TYPE).value(entityType.name())));
  }
}
