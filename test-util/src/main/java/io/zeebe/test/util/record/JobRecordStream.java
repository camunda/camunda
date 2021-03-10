/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.JobRecordValue;
import java.util.stream.Stream;

public final class JobRecordStream
    extends ExporterRecordWithVariablesStream<JobRecordValue, JobRecordStream> {

  public JobRecordStream(final Stream<Record<JobRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected JobRecordStream supply(final Stream<Record<JobRecordValue>> wrappedStream) {
    return new JobRecordStream(wrappedStream);
  }

  public JobRecordStream withType(final String type) {
    return valueFilter(v -> type.equals(v.getType()));
  }

  public JobRecordStream withRetries(final int retries) {
    return valueFilter(v -> v.getRetries() == retries);
  }

  public JobRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public JobRecordStream withElementId(final String elementId) {
    return valueFilter(v -> v.getElementId().equals(elementId));
  }
}
