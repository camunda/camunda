/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static co.elastic.clients.elasticsearch._types.Result.Created;
import static io.camunda.optimize.service.db.DatabaseConstants.JOB_REGISTRY_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryUpdateDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class JobRegistryWriterES implements JobRegistryWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobRegistryWriterES.class);
  private final OptimizeElasticsearchClient esClient;

  public JobRegistryWriterES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public JobRegistryEntryDto createJobEntry(
      final JobType jobType, final EntityType entityType, final String entityId) {
    final JobRegistryEntryDto entry = new JobRegistryEntryDto(jobType, entityType, entityId);
    LOG.debug(
        "Creating job registry entry with id [{}] for [{}] target [{}].",
        entry.getId(),
        jobType,
        entityId);
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, JOB_REGISTRY_INDEX_NAME)
                          .id(entry.getId())
                          .refresh(Refresh.True)
                          .document(entry)));

      if (!indexResponse.result().equals(Created)) {
        throw createJobEntryFailure(entry.getId(), null);
      }
    } catch (final Exception e) {
      throw createJobEntryFailure(entry.getId(), e);
    }
    return entry;
  }

  @Override
  public void updateJobStatus(final String id, final JobStatus status, final String errorMessage) {
    LOG.debug("Updating job registry entry [{}] to status [{}].", id, status);
    final JobRegistryEntryUpdateDto update =
        new JobRegistryEntryUpdateDto(status, errorMessage, LocalDateUtil.getCurrentDateTime());

    final UpdateResponse<JobRegistryEntryUpdateDto> updateResponse;
    try {
      updateResponse =
          esClient.update(
              new OptimizeUpdateRequestBuilderES<
                      JobRegistryEntryUpdateDto, JobRegistryEntryUpdateDto>()
                  .optimizeIndex(esClient, JOB_REGISTRY_INDEX_NAME)
                  .id(id)
                  .doc(update)
                  .refresh(Refresh.True)
                  .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                  .build(),
              JobRegistryEntryUpdateDto.class);
    } catch (final Exception e) {
      throw updateJobStatusFailure(id, e);
    }

    if (!updateResponse.shards().failures().isEmpty()) {
      throw updateJobStatusFailure(id, null);
    }
  }

  private OptimizeRuntimeException createJobEntryFailure(final String id, final Exception cause) {
    final String message =
        String.format("Could not write job registry entry with id [%s] to database.", id);
    if (cause == null) {
      LOG.error(message);
      return new OptimizeRuntimeException(message);
    }
    LOG.error(message, cause);
    return new OptimizeRuntimeException(message, cause);
  }

  private OptimizeRuntimeException updateJobStatusFailure(final String id, final Exception cause) {
    final String message =
        String.format("Was not able to update job registry entry with id [%s].", id);
    if (cause == null) {
      LOG.error(message);
      return new OptimizeRuntimeException(message);
    }
    LOG.error(message, cause);
    return new OptimizeRuntimeException(message, cause);
  }
}
