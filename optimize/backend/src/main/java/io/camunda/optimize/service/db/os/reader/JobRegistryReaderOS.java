/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.JOB_REGISTRY_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.schema.index.JobRegistryIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Optional;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class JobRegistryReaderOS extends JobRegistryReader {

  private final OptimizeOpenSearchClient osClient;

  public JobRegistryReaderOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  protected Optional<JobRegistryEntryDto> performFindOldestQueuedJob(final JobType jobType) {
    final BoolQuery boolQuery =
        new BoolQuery.Builder()
            .must(QueryDSL.term(JobRegistryIndex.JOB_TYPE, jobType.name()))
            .must(QueryDSL.term(JobRegistryIndex.STATUS, JobStatus.QUEUED.name()))
            .build();

    final SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(JOB_REGISTRY_INDEX_NAME)
            .size(1)
            .query(boolQuery.toQuery())
            .sort(
                new SortOptions.Builder()
                    .field(f -> f.field(JobRegistryIndex.CREATED_AT).order(SortOrder.Asc))
                    .build());

    final String errorMessage =
        String.format("Was not able to fetch oldest QUEUED job registry entry for [%s].", jobType);
    final SearchResponse<JobRegistryEntryDto> searchResponse =
        osClient.search(searchReqBuilder, JobRegistryEntryDto.class, errorMessage);

    if (searchResponse.hits().hits().isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(searchResponse.hits().hits().get(0).source());
  }
}
