/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.domain.JobDbModel.Builder;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.InsertJobMerger;
import io.camunda.db.rdbms.write.queue.ListParameterUpsertMerger;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.function.Function;

public class JobWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsWriterConfig config;

  public JobWriter(
      final ExecutionQueue executionQueue,
      final JobMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.config = config;
  }

  public void create(final JobDbModel job) {
    final var truncatedJob =
        job.truncateErrorMessage(
            vendorDatabaseProperties.errorMessageSize(),
            vendorDatabaseProperties.charColumnMaxBytes());

    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new InsertJobMerger(truncatedJob, config.insertBatchingConfig().jobInsertBatchSize()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.JOB,
              WriteStatementType.INSERT,
              -1,
              "io.camunda.db.rdbms.sql.JobMapper.insert",
              new BatchInsertDto<>(truncatedJob)));
    }
  }

  public void update(final JobDbModel job) {
    final boolean wasMerged =
        mergeToQueue(
            job.jobKey(),
            b -> {
              b.type(job.type())
                  .worker(job.worker())
                  .state(job.state())
                  .kind(job.kind())
                  .listenerEventType(job.listenerEventType())
                  .retries(job.retries())
                  .hasFailedWithRetriesLeft(job.hasFailedWithRetriesLeft())
                  .customHeaders(job.customHeaders())
                  .processDefinitionId(job.processDefinitionId())
                  .processDefinitionKey(job.processDefinitionKey())
                  .processInstanceKey(job.processInstanceKey())
                  .elementInstanceKey(job.elementInstanceKey())
                  .tenantId(job.tenantId())
                  .partitionId(job.partitionId())
                  .lastUpdateTime(job.lastUpdateTime());
              // Only update fields when the new value is non-null to avoid overriding existing
              // values with NULL. This covers both @Nullable fields (per JobEntity) and fields
              // that are not always present in update events (e.g. creationTime is only set on
              // CREATED; elementId is explicitly nulled for FAILED/ERROR_THROWN).
              if (job.elementId() != null) {
                b.elementId(job.elementId());
              }
              if (job.creationTime() != null) {
                b.creationTime(job.creationTime());
              }
              if (job.isDenied() != null) {
                b.isDenied(job.isDenied());
              }
              if (job.deniedReason() != null) {
                b.deniedReason(job.deniedReason());
              }
              if (job.errorCode() != null) {
                b.errorCode(job.errorCode());
              }
              if (job.errorMessage() != null) {
                b.errorMessage(job.errorMessage());
              }
              if (job.deadline() != null) {
                b.deadline(job.deadline());
              }
              if (job.endTime() != null) {
                b.endTime(job.endTime());
              }
              if (job.rootProcessInstanceKey() != null) {
                b.rootProcessInstanceKey(job.rootProcessInstanceKey());
              }
              b.truncateErrorMessage(
                  vendorDatabaseProperties.errorMessageSize(),
                  vendorDatabaseProperties.charColumnMaxBytes());
              return b;
            });

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.JOB,
              WriteStatementType.UPDATE,
              job.jobKey(),
              "io.camunda.db.rdbms.sql.JobMapper.update",
              job.truncateErrorMessage(
                  vendorDatabaseProperties.errorMessageSize(),
                  vendorDatabaseProperties.charColumnMaxBytes())));
    }
  }

  private boolean mergeToQueue(final long key, final Function<Builder, Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new ListParameterUpsertMerger<>(ContextType.JOB, key, JobDbModel::jobKey, mergeFunction));
  }
}
