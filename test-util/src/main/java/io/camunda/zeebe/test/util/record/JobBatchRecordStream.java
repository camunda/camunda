/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import java.util.stream.Stream;

public final class JobBatchRecordStream
    extends ExporterRecordStream<JobBatchRecordValue, JobBatchRecordStream> {

  public JobBatchRecordStream(final Stream<Record<JobBatchRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected JobBatchRecordStream supply(final Stream<Record<JobBatchRecordValue>> wrappedStream) {
    return new JobBatchRecordStream(wrappedStream);
  }

  public JobBatchRecordStream withType(final String type) {
    return valueFilter(v -> type.equals(v.getType()));
  }
}
