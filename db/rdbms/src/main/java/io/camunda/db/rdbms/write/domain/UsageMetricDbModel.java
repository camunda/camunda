/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import java.time.OffsetDateTime;

public record UsageMetricDbModel(
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    String tenantId,
    EventType eventType,
    long value,
    int partitionId,
    OffsetDateTime historyCleanupDate) {

  public String id() {
    return "%d-%d-%s-%s-%d"
        .formatted(
            startTime.toEpochSecond(), endTime.toEpochSecond(), tenantId, eventType, partitionId);
  }

  public record UsageMetricStatisticsDbModel(String tenantId, Long rpi, Long edi) {}

  public record UsageMetricStatisticsDbModel2(Long rpi, Long edi, Long at) {}

  public static class Builder implements ObjectBuilder<UsageMetricDbModel> {

    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String tenantId;
    private EventType eventType;
    private long value;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

    public Builder startTime(final OffsetDateTime startTime) {
      this.startTime = startTime;
      return this;
    }

    public Builder endTime(final OffsetDateTime endTime) {
      this.endTime = endTime;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder eventType(final EventType eventType) {
      this.eventType = eventType;
      return this;
    }

    public Builder value(final long value) {
      this.value = value;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
      this.historyCleanupDate = historyCleanupDate;
      return this;
    }

    @Override
    public UsageMetricDbModel build() {
      return new UsageMetricDbModel(
          startTime, endTime, tenantId, eventType, value, partitionId, historyCleanupDate);
    }
  }
}
