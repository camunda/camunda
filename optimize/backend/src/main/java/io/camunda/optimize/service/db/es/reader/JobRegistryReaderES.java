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
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.schema.index.JobRegistryIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class JobRegistryReaderES extends JobRegistryReader {

  private final OptimizeElasticsearchClient esClient;

  public JobRegistryReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  protected Optional<JobRegistryEntryDto> performFindOldestQueuedJob(final JobType jobType)
      throws IOException {
    final Query query =
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.must(
                                m ->
                                    m.term(
                                        t ->
                                            t.field(JobRegistryIndex.JOB_TYPE)
                                                .value(jobType.name())))
                            .must(
                                m ->
                                    m.term(
                                        t ->
                                            t.field(JobRegistryIndex.STATUS)
                                                .value(JobStatus.QUEUED.name())))));

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, JOB_REGISTRY_INDEX_NAME)
                    .query(query)
                    .sort(
                        sort ->
                            sort.field(
                                f -> f.field(JobRegistryIndex.CREATED_AT).order(SortOrder.Asc)))
                    .size(1));

    final SearchResponse<JobRegistryEntryDto> searchResponse =
        esClient.search(searchRequest, JobRegistryEntryDto.class);

    if (searchResponse.hits().total().value() == 0) {
      return Optional.empty();
    }
    return Optional.ofNullable(searchResponse.hits().hits().get(0).source());
  }

  @Override
  protected Optional<JobRegistryEntryDto> performFindByJobTypeAndTargetEntityId(
      final JobType jobType, final String targetEntityId) throws IOException {
    final Query query =
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.must(
                                m ->
                                    m.term(
                                        t ->
                                            t.field(JobRegistryIndex.JOB_TYPE)
                                                .value(jobType.name())))
                            .must(
                                m ->
                                    m.term(
                                        t ->
                                            t.field(JobRegistryIndex.TARGET_ENTITY_ID)
                                                .value(targetEntityId)))));

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

    final SearchResponse<JobRegistryEntryDto> searchResponse =
        esClient.search(searchRequest, JobRegistryEntryDto.class);

    if (searchResponse.hits().total().value() == 0) {
      return Optional.empty();
    }
    return Optional.ofNullable(searchResponse.hits().hits().get(0).source());
  }
}
