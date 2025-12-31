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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Record implementation for batch export of job worker metrics. Contains aggregated job state
 * counters for job types and workers within a time window.
 */
public final class JobMetricsBatchRecord extends UnifiedRecordValue
    implements JobMetricsBatchRecordValue {

  private final LongProperty batchStartTimeProperty = new LongProperty("batchStartTime", -1L);
  private final LongProperty batchEndTimeProperty = new LongProperty("batchEndTime", -1L);
  private final BooleanProperty hasTooManyBucketsProperty =
      new BooleanProperty("hasTooManyBuckets", false);
  private final BooleanProperty hasFieldLimitExceededProperty =
      new BooleanProperty("hasFieldLimitExceeded", false);
  private final ArrayProperty<StringValue> encodedStringsProperty =
      new ArrayProperty<>("encodedStrings", StringValue::new);
  private final ArrayProperty<JobMetricsBatchValueRecord> metricsProperty =
      new ArrayProperty<>("metrics", JobMetricsBatchValueRecord::new);

  public JobMetricsBatchRecord() {
    super(6);
    declareProperty(batchStartTimeProperty)
        .declareProperty(batchEndTimeProperty)
        .declareProperty(hasTooManyBucketsProperty)
        .declareProperty(hasFieldLimitExceededProperty)
        .declareProperty(encodedStringsProperty)
        .declareProperty(metricsProperty);
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
  public boolean getHasTooManyBuckets() {
    return hasTooManyBucketsProperty.getValue();
  }

  public JobMetricsBatchRecord setHasTooManyBuckets(final boolean hasTooManyBuckets) {
    hasTooManyBucketsProperty.setValue(hasTooManyBuckets);
    return this;
  }

  @Override
  public boolean getHasFieldLimitExceeded() {
    return hasFieldLimitExceededProperty.getValue();
  }

  public JobMetricsBatchRecord setHasFieldLimitExceeded(final boolean hasFieldLimitExceeded) {
    hasFieldLimitExceededProperty.setValue(hasFieldLimitExceeded);
    return this;
  }

  @Override
  public List<String> getEncodedStrings() {
    return StreamSupport.stream(encodedStringsProperty.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  public JobMetricsBatchRecord setEncodedStrings(final List<String> values) {
    encodedStringsProperty.reset();
    values.forEach(this::addEncodedString);
    return this;
  }

  @Override
  public Map<String, JobMetricsBatchValue> getMetricsByTypeAndTenant() {
    final Map<String, JobMetricsBatchValue> result = new HashMap<>();
    for (final JobMetricsBatchValueRecord record : metricsProperty) {
      final String key = record.getJobTypeIndex() + "_" + record.getTenantIdIndex();
      result.put(key, record);
    }
    return result;
  }

  public JobMetricsBatchRecord addEncodedString(final String value) {
    encodedStringsProperty.add().wrap(BufferUtil.wrapString(value));
    return this;
  }

  /**
   * Returns the index of the encoded string, adding it if not present.
   *
   * @param value the string to encode
   * @return the index of the string in the encoded strings list
   */
  public int getOrAddEncodedStringIndex(final String value) {
    int index = 0;
    for (final StringValue sv : encodedStringsProperty) {
      if (BufferUtil.bufferAsString(sv.getValue()).equals(value)) {
        return index;
      }
      index++;
    }
    addEncodedString(value);
    return index;
  }

  public JobMetricsBatchRecord addMetric(final JobMetricsBatchValueRecord metric) {
    metricsProperty.add().copyFrom(metric);
    return this;
  }

  /**
   * Gets or creates a metrics value record for the given job type and tenant indices.
   *
   * @param jobTypeIndex the index of the job type in the encoded strings
   * @param tenantIdIndex the index of the tenant ID in the encoded strings
   * @return the existing or newly created metrics value record
   */
  public JobMetricsBatchValueRecord getOrCreateMetric(
      final int jobTypeIndex, final int tenantIdIndex) {
    for (final JobMetricsBatchValueRecord record : metricsProperty) {
      if (record.getJobTypeIndex() == jobTypeIndex && record.getTenantIdIndex() == tenantIdIndex) {
        return record;
      }
    }
    final JobMetricsBatchValueRecord newRecord = metricsProperty.add();
    newRecord.setJobTypeIndex(jobTypeIndex);
    newRecord.setTenantIdIndex(tenantIdIndex);
    return newRecord;
  }
}
