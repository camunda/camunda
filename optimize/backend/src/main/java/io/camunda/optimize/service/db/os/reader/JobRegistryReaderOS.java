/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.JOB_REGISTRY_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.schema.index.JobRegistryIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class JobRegistryReaderOS implements JobRegistryReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobRegistryReaderOS.class);
  private final OptimizeOpenSearchClient osClient;

  public JobRegistryReaderOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public List<JobRegistryEntryDto> findOldestQueuedJobs(final int limit) {
    LOG.debug("Fetching up to [{}] oldest QUEUED job registry entries.", limit);
    final BoolQuery boolQuery =
        new BoolQuery.Builder()
            .must(QueryDSL.term(JobRegistryIndex.STATUS, JobStatus.QUEUED.name()))
            .build();

    final SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(JOB_REGISTRY_INDEX_NAME)
            .size(limit)
            .query(boolQuery.toQuery())
            .sort(
                List.of(
                    new SortOptions.Builder()
                        .field(f -> f.field(JobRegistryIndex.CREATED_AT).order(SortOrder.Asc))
                        .build(),
                    // tiebreaker
                    new SortOptions.Builder()
                        .field(f -> f.field(JobRegistryIndex.ID).order(SortOrder.Asc))
                        .build()));

    final String errorMessage =
        String.format(
            "Was not able to fetch oldest QUEUED job registry entries (limit [%s]).", limit);
    final SearchResponse<JobRegistryEntryDto> searchResponse =
        osClient.search(searchReqBuilder, JobRegistryEntryDto.class, errorMessage);

    return searchResponse.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
  }

  @Override
  public Optional<JobRegistryEntryDto> findLastByJobTypeAndEntityId(
      final JobType jobType, final EntityType entityType, final String entityId) {
    LOG.debug(
        "Fetching job registry entry for [{}] entity type [{}] entity [{}].",
        jobType,
        entityType,
        entityId);
    final BoolQuery boolQuery =
        jobTypeAndEntityTypeQuery(jobType, entityType)
            .must(QueryDSL.term(JobRegistryIndex.ENTITY_ID, entityId))
            .build();

    final SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(JOB_REGISTRY_INDEX_NAME)
            .size(1)
            .query(boolQuery.toQuery())
            .sort(
                new SortOptions.Builder()
                    .field(f -> f.field(JobRegistryIndex.CREATED_AT).order(SortOrder.Desc))
                    .build());

    final String errorMessage =
        String.format(
            "Was not able to fetch job registry entry for [%s] entity type [%s] entity [%s].",
            jobType, entityType, entityId);
    final SearchResponse<JobRegistryEntryDto> searchResponse =
        osClient.search(searchReqBuilder, JobRegistryEntryDto.class, errorMessage);

    if (searchResponse.hits().hits().isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(searchResponse.hits().hits().getFirst().source());
  }

  @Override
  public Set<String> findEntityIdsWithJob(
      final JobType jobType, final EntityType entityType, final Collection<String> entityIds) {
    if (entityIds.isEmpty()) {
      return Set.of();
    }
    LOG.debug(
        "Fetching entity IDs with [{}] job entries for entity type [{}] among [{}] candidates.",
        jobType,
        entityType,
        entityIds.size());

    final BoolQuery boolQuery =
        jobTypeAndEntityTypeQuery(jobType, entityType)
            .must(QueryDSL.stringTerms(JobRegistryIndex.ENTITY_ID, entityIds))
            .build();

    final SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(JOB_REGISTRY_INDEX_NAME)
            .size(entityIds.size())
            .query(boolQuery.toQuery())
            .collapse(c -> c.field(JobRegistryIndex.ENTITY_ID))
            .source(QueryDSL.sourceInclude(List.of(JobRegistryIndex.ENTITY_ID)));

    final String errorMessage =
        String.format(
            "Was not able to fetch entity IDs with [%s] job entries for entity type [%s].",
            jobType, entityType);
    final SearchResponse<JobRegistryEntryDto> searchResponse =
        osClient.search(searchReqBuilder, JobRegistryEntryDto.class, errorMessage);

    return searchResponse.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .map(JobRegistryEntryDto::getEntityId)
        .collect(Collectors.toSet());
  }

  private BoolQuery.Builder jobTypeAndEntityTypeQuery(
      final JobType jobType, final EntityType entityType) {
    return new BoolQuery.Builder()
        .must(QueryDSL.term(JobRegistryIndex.JOB_TYPE, jobType.name()))
        .must(QueryDSL.term(JobRegistryIndex.ENTITY_TYPE, entityType.name()));
  }
}
