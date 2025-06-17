/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public record UsageMetricDbModel(
    String id,
    OffsetDateTime eventTime,
    String tenantId,
    EventTypeDbModel eventType,
    long value,
    int partitionId) {

  public record UsageMetricStatisticsDbModel(Long rpi, Long edi, Long at) {}

  public record UsageMetricTenantStatisticsDbModel(String tenantId, Long rpi, Long edi) {}

  public static class Builder implements ObjectBuilder<UsageMetricDbModel> {

    private String id;
    private OffsetDateTime eventTime;
    private String tenantId;
    private EventTypeDbModel eventType;
    private long value;
    private int partitionId;

    public Builder id(final String id) {
      this.id = id;
      return this;
    }

    public Builder eventTime(final OffsetDateTime eventTime) {
      this.eventTime = eventTime;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder eventType(final EventTypeDbModel eventType) {
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

    @Override
    public UsageMetricDbModel build() {
      return new UsageMetricDbModel(id, eventTime, tenantId, eventType, value, partitionId);
    }
  }

  public enum EventTypeDbModel {
    RPI(0),
    EDI(1);

    private final int code;

    EventTypeDbModel(final int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }

    public static EventTypeDbModel fromCode(final int code) {
      for (final EventTypeDbModel eventTypeDbModel : values()) {
        if (eventTypeDbModel.code == code) {
          return eventTypeDbModel;
        }
      }
      return null;
    }
  }
}
