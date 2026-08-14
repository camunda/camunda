/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.JOB_REGISTRY_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryUpdateDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.dto.optimize.query.job.TargetEntityType;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class JobRegistryWriterOS implements JobRegistryWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobRegistryWriterOS.class);
  private final OptimizeOpenSearchClient osClient;

  public JobRegistryWriterOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public JobRegistryEntryDto createJobEntry(
      final JobType jobType, final TargetEntityType targetEntityType, final String targetEntityId) {
    final JobRegistryEntryDto entry =
        new JobRegistryEntryDto(jobType, targetEntityType, targetEntityId);
    LOG.debug(
        "Creating job registry entry with id [{}] for [{}] target [{}].",
        entry.getId(),
        jobType,
        targetEntityId);

    final IndexRequest.Builder<JobRegistryEntryDto> request =
        new IndexRequest.Builder<JobRegistryEntryDto>()
            .index(JOB_REGISTRY_INDEX_NAME)
            .id(entry.getId())
            .document(entry)
            .refresh(Refresh.True);
    final IndexResponse indexResponse = osClient.index(request);

    if (!indexResponse.result().equals(Result.Created)) {
      final String message =
          String.format(
              "Could not write job registry entry with id [%s] to database.", entry.getId());
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }
    return entry;
  }

  @Override
  public void updateJobStatus(final String id, final JobStatus status, final String errorMessage) {
    LOG.debug("Updating job registry entry [{}] to status [{}].", id, status);
    final JobRegistryEntryUpdateDto update =
        new JobRegistryEntryUpdateDto(status, errorMessage, LocalDateUtil.getCurrentDateTime());

    final UpdateRequest.Builder<Void, JobRegistryEntryUpdateDto> request =
        new UpdateRequest.Builder<Void, JobRegistryEntryUpdateDto>()
            .index(JOB_REGISTRY_INDEX_NAME)
            .id(id)
            .doc(update)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final String updateErrorMessage =
        String.format("Was not able to update job registry entry with id [%s].", id);
    final UpdateResponse<Void> updateResponse = osClient.update(request, updateErrorMessage);

    if (updateResponse.shards().failed() > 0) {
      LOG.error(updateErrorMessage);
      throw new OptimizeRuntimeException(updateErrorMessage);
    }
  }
}
