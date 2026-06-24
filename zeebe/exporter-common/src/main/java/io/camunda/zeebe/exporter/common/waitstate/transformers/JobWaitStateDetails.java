/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateDetails;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import org.jspecify.annotations.Nullable;

/**
 * Wait-state details for job-based element waits (service tasks, send tasks, script tasks,
 * business-rule tasks).
 */
public record JobWaitStateDetails(
    long jobKey,
    String jobType,
    JobKind jobKind,
    /**
     * Non-null only for {@link JobKind#EXECUTION_LISTENER} and {@link JobKind#TASK_LISTENER} jobs.
     */
    @Nullable JobListenerEventType listenerEventType,
    int retries)
    implements WaitStateDetails {}
