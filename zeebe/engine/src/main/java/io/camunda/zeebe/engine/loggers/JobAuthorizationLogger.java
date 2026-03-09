/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.loggers;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.cache.DeduplicationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobAuthorizationLogger {

  private static final Logger LOG = LoggerFactory.getLogger(JobAuthorizationLogger.class);

  private final DeduplicationCache cache;

  private JobAuthorizationLogger(final DeduplicationCache cache) {
    this.cache = cache;
  }

  public static JobAuthorizationLogger createDefault() {
    return new JobAuthorizationLogger(DeduplicationCache.createDefault());
  }

  public void logUnauthorizedTenantAccess(
      final JobActivationProperties jobActivationProperties, final JobRecord jobRecord) {
    final String ownerTenantId = jobRecord.getTenantId();
    final String processId = jobRecord.getBpmnProcessId();
    final String workerName = BufferUtil.bufferAsString(jobActivationProperties.worker());

    final var logKey = buildLogKey(ownerTenantId, processId, workerName);
    if (cache.isFirstOccurrence(logKey)) {
      LOG.warn(
          "Job stream for worker '{}' requesting jobs of type '{}' is not authorized to access tenant '{}'. "
              + "Job for process '{}' will not be pushed to this stream. "
              + "Tenant filter: {}",
          workerName,
          jobRecord.getType(),
          ownerTenantId,
          processId,
          jobActivationProperties.tenantFilter());
    }
  }

  public void logUnauthorizedResourceAccess(
      final JobActivationProperties jobActivationProperties, final JobRecord jobRecord) {
    final String ownerTenantId = jobRecord.getTenantId();
    final String processId = jobRecord.getBpmnProcessId();
    final String workerName = BufferUtil.bufferAsString(jobActivationProperties.worker());
    final var logKey = buildLogKey(ownerTenantId, processId, workerName);
    if (cache.isFirstOccurrence(logKey)) {
      LOG.warn(
          "Job stream for worker '{}' requesting jobs of type '{}' is not authorized to access process definition '{}' in tenant '{}'. "
              + "Job will not be pushed to this stream. Required permission: {}",
          workerName,
          jobRecord.getType(),
          processId,
          ownerTenantId,
          PermissionType.UPDATE_PROCESS_INSTANCE);
    }
  }

  private String buildLogKey(
      final String ownerTenantId, final String processId, final String workerName) {
    return String.format("tenant:%s:processId:%s:worker:%s", ownerTenantId, processId, workerName);
  }
}
