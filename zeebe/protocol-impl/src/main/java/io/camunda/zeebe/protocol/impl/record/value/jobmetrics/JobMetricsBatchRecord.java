/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.jobmetrics;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class JobMetricsBatchRecord extends UnifiedRecordValue
    implements JobMetricsBatchRecordValue {

  private final LongProperty batchStartTimeProperty = new LongProperty("batchStartTime", -1L);
  private final LongProperty batchEndTimeProperty = new LongProperty("batchEndTime", -1L);

  private final BooleanProperty recordSizeLimitExceededProperty =
      new BooleanProperty("recordSizeLimitExceeded", false);

  private final ArrayProperty<StringValue> encodedStringsProperty =
      new ArrayProperty<>("encodedStrings", StringValue::new);

  private final ArrayProperty<JobMetrics> jobMetricsProperty =
      new ArrayProperty<>("jobMetrics", JobMetrics::new);

  public JobMetricsBatchRecord() {
    super(5);
    declareProperty(batchStartTimeProperty);
    declareProperty(batchEndTimeProperty);
    declareProperty(recordSizeLimitExceededProperty);
    declareProperty(encodedStringsProperty);
    declareProperty(jobMetricsProperty);
  }

  @Override
  public long getBatchStartTime() {
    return batchStartTimeProperty.getValue();
  }

  public JobMetricsBatchRecord setBatchStartTime(final long batchStartTime) {
    batchStartTimeProperty.setValue(batchStartTime);
    return this;
  }

  @Override
  public long getBatchEndTime() {
    return batchEndTimeProperty.getValue();
  }

  public JobMetricsBatchRecord setBatchEndTime(final long batchEndTime) {
    batchEndTimeProperty.setValue(batchEndTime);
    return this;
  }

  @Override
  public boolean getRecordSizeLimitExceeded() {
    return recordSizeLimitExceededProperty.getValue();
  }

  public JobMetricsBatchRecord setRecordSizeLimitExceeded(final boolean recordSizeLimitExceeded) {
    recordSizeLimitExceededProperty.setValue(recordSizeLimitExceeded);
    return this;
  }

  @Override
  public List<String> getEncodedStrings() {
    return StreamSupport.stream(encodedStringsProperty.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  @Override
  public List<JobMetricsValue> getJobMetrics() {
    return jobMetricsProperty.stream().collect(Collectors.toList());
  }

  public JobMetricsBatchRecord setJobMetrics(final Collection<JobMetricsValue> jobMetrics) {
    jobMetricsProperty.reset();
    jobMetrics.forEach(jm -> jobMetricsProperty.add().wrap(jm));
    return this;
  }

  public JobMetricsBatchRecord setEncodedStrings(final Set<String> encodedStrings) {
    encodedStringsProperty.reset();
    encodedStrings.forEach(str -> encodedStringsProperty.add().wrap(BufferUtil.wrapString(str)));
    return this;
  }
}
