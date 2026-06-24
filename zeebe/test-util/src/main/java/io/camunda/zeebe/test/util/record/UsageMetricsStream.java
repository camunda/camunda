/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import java.util.stream.Stream;

public class UsageMetricsStream
    extends ExporterRecordStream<UsageMetricRecordValue, UsageMetricsStream> {

  public UsageMetricsStream(final Stream<Record<UsageMetricRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected UsageMetricsStream supply(final Stream<Record<UsageMetricRecordValue>> wrappedStream) {
    return new UsageMetricsStream(wrappedStream);
  }

  public UsageMetricsStream withEventType(final EventType eventType) {
    return valueFilter(v -> v.getEventType() == eventType);
  }
}
