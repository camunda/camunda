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
import java.util.HashMap;
import java.util.Map;

public class JobBasedWaitStateTransformer implements WaitStateTransformer<JobRecordValue> {

  public static final String DETAIL_JOB_KEY = "jobKey";
  public static final String DETAIL_JOB_TYPE = "jobType";
  public static final String DETAIL_JOB_KIND = "jobKind";
  public static final String DETAIL_LISTENER_EVENT_TYPE = "listenerEventType";
  public static final String DETAIL_RETRIES = "retries";

  @Override
  public WaitStateTransformerConfig config() {
    return JOB_CONFIG;
  }

  @Override
  public void extract(final Record<JobRecordValue> record, final WaitStateEntry entry) {
    final JobRecordValue value = record.getValue();

    final Map<String, Object> details = new HashMap<>();
    details.put(DETAIL_JOB_KEY, record.getKey());
    details.put(DETAIL_JOB_TYPE, value.getType());
    details.put(DETAIL_JOB_KIND, value.getJobKind().name());
    details.put(DETAIL_LISTENER_EVENT_TYPE, value.getJobListenerEventType().name());
    details.put(DETAIL_RETRIES, value.getRetries());

    entry.setElementType(value.getElementType()).setDetails(details);
  }
}
