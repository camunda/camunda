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
import io.camunda.zeebe.protocol.record.value.JobRecordValue;

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
                value.getJobListenerEventType(),
                value.getRetries()));
  }
}
