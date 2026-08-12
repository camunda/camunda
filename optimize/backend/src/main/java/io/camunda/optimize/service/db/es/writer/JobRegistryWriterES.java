/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.JOB_REGISTRY_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryUpdateDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class JobRegistryWriterES extends JobRegistryWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobRegistryWriterES.class);
  private final OptimizeElasticsearchClient esClient;

  public JobRegistryWriterES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  protected void performCreatingJobEntry(final JobRegistryEntryDto entry) throws IOException {
    esClient.index(
        OptimizeIndexRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, JOB_REGISTRY_INDEX_NAME)
                    .id(entry.getId())
                    .refresh(Refresh.True)
                    .document(entry)));
  }

  @Override
  protected void performUpdatingJobStatus(final String id, final JobRegistryEntryUpdateDto update)
      throws IOException {
    final UpdateResponse<JobRegistryEntryUpdateDto> updateResponse =
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

    if (!updateResponse.shards().failures().isEmpty()) {
      final String message =
          String.format("Was not able to update job registry entry with id [%s].", id);
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }
}
