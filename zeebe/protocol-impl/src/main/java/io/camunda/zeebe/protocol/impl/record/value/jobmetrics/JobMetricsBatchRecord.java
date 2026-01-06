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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    final List<String> result = new ArrayList<>();
    for (final StringValue stringValue : encodedStringsProperty) {
      result.add(BufferUtil.bufferAsString(stringValue.getValue()));
    }
    return result;
  }

  @Override
  public Map<String, JobMetricsValue> getJobMetrics() {
    final Map<String, JobMetricsValue> result = new HashMap<>();
    for (final JobMetrics record : jobMetricsProperty) {
      final String key = record.getJobTypeIndex() + "_" + record.getTenantIdIndex();
      result.put(key, record);
    }
    return result;
  }
}
