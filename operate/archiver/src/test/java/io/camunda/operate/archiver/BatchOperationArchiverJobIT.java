/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BatchOperationArchiverJobIT extends ArchiverJobIT {

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Override
  protected ArchiverJob createArchiveJob(final List<Integer> partitionIds) {
    return new BatchOperationArchiverJob(
        getArchiver(), batchOperationTemplate, getMetrics(), getArchiverRepository());
  }

  @Test
  void shouldArchiveBatchOperation() throws Exception {
    withArchiverJob(
        job -> {
          final var batchOp = batchOperationEntity("2020-01-01T00:00:00+00:00");
          store(batchOperationTemplate, batchOp);
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(batchOperationTemplate, batchOp.getId(), "2020-01-01");
        });
  }

  @Test
  void shouldNotArchiveRecentBatchOperation() throws Exception {
    withArchiverJob(
        job -> {
          final var batchOp = batchOperationEntity("2099-01-01T00:00:00+00:00");
          store(batchOperationTemplate, batchOp);
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(0);

          verifyNotMoved(batchOperationTemplate, batchOp.getId());
        });
  }

  private BatchOperationEntity batchOperationEntity(final String endDate) {
    final var entity = new BatchOperationEntity();
    entity.setId(String.valueOf(ID_GENERATOR.incrementAndGet()));
    entity.setEndDate(OffsetDateTime.parse(endDate));
    return entity;
  }
}
