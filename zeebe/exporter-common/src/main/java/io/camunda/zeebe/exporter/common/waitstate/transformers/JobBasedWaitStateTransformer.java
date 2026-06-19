/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static io.camunda.zeebe.exporter.common.waitstate.WaitStateConfigs.JOB_CONFIG;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformerConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import org.jspecify.annotations.Nullable;

public class JobBasedWaitStateTransformer implements WaitStateTransformer<JobRecordValue> {

  @Override
  public WaitStateTransformerConfig config() {
    return JOB_CONFIG;
  }

  @Override
  public void extract(final Record<JobRecordValue> record, final WaitStateEntry entry) {
    final JobRecordValue value = record.getValue();
    entry
        .setElementType(value.getElementType())
        .setDetails(
            new JobWaitStateDetails(
                record.getKey(),
                value.getType(),
                value.getJobKind(),
                listenerEventType(value),
                value.getRetries()));
  }

  private static @Nullable JobListenerEventType listenerEventType(final JobRecordValue value) {
    return isListenerJob(value.getJobKind()) ? value.getJobListenerEventType() : null;
  }

  private static boolean isListenerJob(final JobKind jobKind) {
    return switch (jobKind) {
      case EXECUTION_LISTENER, TASK_LISTENER -> true;
      // MAINTENANCE jobs are engine-internal (filtered out of ActivateJobs by JobBatchCollector);
      // a worker never sees them as a wait-state, so they group with the non-listener bucket.
      case BPMN_ELEMENT, AD_HOC_SUB_PROCESS, MAINTENANCE -> false;
    };
  }
}
