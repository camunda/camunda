/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.jobmetrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricsValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties("length")
public final class StatusMetrics extends MsgpackBufferValue<StatusMetrics>
    implements StatusMetricsValue {

  int count = -1;
  long lastUpdatedAt = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public int getCount() {
    return count;
  }

  @Override
  public long getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public StatusMetrics setLastUpdatedAt(final long lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
    return this;
  }

  public StatusMetrics setCount(final int count) {
    this.count = count;
    return this;
  }

  public void wrap(final StatusMetricsValue statusMetricsValue) {
    setCount(statusMetricsValue.getCount());
    setLastUpdatedAt(statusMetricsValue.getLastUpdatedAt());
  }

  /** Kept so that the exported JSON stays unchanged. */
  public boolean isEmpty() {
    return false;
  }

  /** Kept so that the exported JSON stays unchanged. */
  public int getEncodedLength() {
    return getLength();
  }

  @Override
  protected MsgpackCodec<StatusMetrics> codec() {
    return StatusMetricsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<StatusMetrics> ops() {
    return StatusMetricsOps.INSTANCE;
  }
}
