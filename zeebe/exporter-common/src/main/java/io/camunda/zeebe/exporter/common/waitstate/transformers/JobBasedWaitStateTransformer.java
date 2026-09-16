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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
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
    clearElementIdIfSentinelRisk(record, entry);
    entry
        .setElementType(value.getElementType())
        .setDetails(
            new JobWaitStateDetails(
                record.getKey(),
                value.getType(),
                value.getJobKind(),
                listenerEventType(value),
                value.getRetries(),
                // The pending flag is derived per record from the intent, not from prior wait
                // state. Each update replaces the stored details wholesale, so an unrelated update
                // on a still-parked job (e.g. RETRIES_UPDATED or MIGRATED) transiently clears this
                // flag until the next SECRET_RESOLUTION_PARKED re-parks it. This is acceptable:
                // such updates are rare on parked jobs, and the following resolution attempt either
                // resumes the job (SECRET_RESOLUTION_RESUMED) or re-parks it. FAILED cannot occur
                // while parked, since failing a job requires it to be ACTIVATED.
                record.getIntent() == JobIntent.SECRET_RESOLUTION_PARKED));
  }

  /**
   * FAILED and RETRIES_UPDATED records may carry "NO_CATCH_EVENT_FOUND" as elementId when a BPMN
   * error has no matching catch event. Nulling elementId here prevents update handlers from
   * overwriting the stored elementId with that sentinel value.
   */
  private static void clearElementIdIfSentinelRisk(
      final Record<JobRecordValue> record, final WaitStateEntry entry) {
    if (record.getIntent() == JobIntent.FAILED || record.getIntent() == JobIntent.RETRIES_UPDATED) {
      entry.setElementId(null);
    }
  }

  private static @Nullable JobListenerEventType listenerEventType(final JobRecordValue value) {
    return isListenerJob(value.getJobKind()) ? value.getJobListenerEventType() : null;
  }

  private static boolean isListenerJob(final JobKind jobKind) {
    return switch (jobKind) {
      case EXECUTION_LISTENER, TASK_LISTENER -> true;
      case BPMN_ELEMENT, AD_HOC_SUB_PROCESS -> false;
    };
  }
}
