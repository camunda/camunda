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
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class JobRegistryWriterOS extends JobRegistryWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobRegistryWriterOS.class);
  private final OptimizeOpenSearchClient osClient;

  public JobRegistryWriterOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  protected void performCreatingJobEntry(final JobRegistryEntryDto entry) {
    final IndexRequest.Builder<JobRegistryEntryDto> request =
        new IndexRequest.Builder<JobRegistryEntryDto>()
            .index(JOB_REGISTRY_INDEX_NAME)
            .id(entry.getId())
            .document(entry)
            .refresh(Refresh.True);
    osClient.index(request);
  }

  @Override
  protected void performUpdatingJobStatus(final String id, final JobRegistryEntryUpdateDto update) {
    final UpdateRequest.Builder<Void, JobRegistryEntryUpdateDto> request =
        new UpdateRequest.Builder<Void, JobRegistryEntryUpdateDto>()
            .index(JOB_REGISTRY_INDEX_NAME)
            .id(id)
            .doc(update)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final String errorMessage =
        String.format("Was not able to update job registry entry with id [%s].", id);
    final UpdateResponse<Void> updateResponse = osClient.update(request, errorMessage);

    if (updateResponse.shards().failed() > 0) {
      final String message =
          String.format("Was not able to update job registry entry with id [%s].", id);
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }
}
