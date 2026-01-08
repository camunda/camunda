/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Key for the metrics column family. Contains three integer indices: jobTypeIndex, tenantIdIndex,
 * workerNameIndex.
 *
 * <p>Total size: 12 bytes (3 x 4 bytes)
 */
public final class MetricsKey implements DbKey, DbValue {

  /** Fixed size: 3 integers = 12 bytes */
  public static final int TOTAL_SIZE_BYTES = 3 * Integer.BYTES;

  private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

  private int jobTypeIndex;
  private int tenantIdIndex;
  private int workerNameIndex;

  public MetricsKey() {}

  public MetricsKey(final int jobTypeIndex, final int tenantIdIndex, final int workerNameIndex) {
    this.jobTypeIndex = jobTypeIndex;
    this.tenantIdIndex = tenantIdIndex;
    this.workerNameIndex = workerNameIndex;
  }

  public int getJobTypeIndex() {
    return jobTypeIndex;
  }

  public void setJobTypeIndex(final int jobTypeIndex) {
    this.jobTypeIndex = jobTypeIndex;
  }

  public int getTenantIdIndex() {
    return tenantIdIndex;
  }

  public void setTenantIdIndex(final int tenantIdIndex) {
    this.tenantIdIndex = tenantIdIndex;
  }

  public int getWorkerNameIndex() {
    return workerNameIndex;
  }

  public void setWorkerNameIndex(final int workerNameIndex) {
    this.workerNameIndex = workerNameIndex;
  }

  public void set(final int jobTypeIndex, final int tenantIdIndex, final int workerNameIndex) {
    this.jobTypeIndex = jobTypeIndex;
    this.tenantIdIndex = tenantIdIndex;
    this.workerNameIndex = workerNameIndex;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    jobTypeIndex = buffer.getInt(offset, BYTE_ORDER);
    tenantIdIndex = buffer.getInt(offset + Integer.BYTES, BYTE_ORDER);
    workerNameIndex = buffer.getInt(offset + 2 * Integer.BYTES, BYTE_ORDER);
  }

  @Override
  public int getLength() {
    return TOTAL_SIZE_BYTES;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putInt(offset, jobTypeIndex, BYTE_ORDER);
    buffer.putInt(offset + Integer.BYTES, tenantIdIndex, BYTE_ORDER);
    buffer.putInt(offset + 2 * Integer.BYTES, workerNameIndex, BYTE_ORDER);
  }

  @Override
  public String toString() {
    return "MetricsKey{"
        + "jobTypeIndex="
        + jobTypeIndex
        + ", tenantIdIndex="
        + tenantIdIndex
        + ", workerNameIndex="
        + workerNameIndex
        + '}';
  }
}
