/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbString;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Represents the key for the JobMetrics column family. The key is a composite of jobType and
 * tenantId, formatted as: jobType_tenantId
 */
public final class JobMetricsKey implements DbKey {

  private final DbString jobType;
  private final DbString tenantId;

  public JobMetricsKey() {
    jobType = new DbString();
    tenantId = new DbString();
  }

  public String getJobType() {
    return jobType.toString();
  }

  public void setJobType(final String jobType) {
    this.jobType.wrapString(jobType);
  }

  public void setJobType(final DirectBuffer jobType) {
    this.jobType.wrapBuffer(jobType);
  }

  public String getTenantId() {
    return tenantId.toString();
  }

  public void setTenantId(final String tenantId) {
    this.tenantId.wrapString(tenantId);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    jobType.wrap(buffer, offset, length);
    final int jobTypeLength = jobType.getLength();
    tenantId.wrap(buffer, offset + jobTypeLength, length - jobTypeLength);
  }

  @Override
  public int getLength() {
    return jobType.getLength() + tenantId.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    jobType.write(buffer, offset);
    tenantId.write(buffer, offset + jobType.getLength());
  }
}
