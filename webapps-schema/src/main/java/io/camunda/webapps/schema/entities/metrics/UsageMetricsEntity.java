/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.metrics;

import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class UsageMetricsEntity
    implements ExporterEntity<UsageMetricsEntity>,
        PartitionedEntity<UsageMetricsEntity>,
        TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private OffsetDateTime startTime;
  @BeforeVersion880 private OffsetDateTime endTime;
  @BeforeVersion880 private UsageMetricsEventType eventType;
  @BeforeVersion880 private Long eventValue;
  @BeforeVersion880 private String tenantId;
  @BeforeVersion880 private int partitionId;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public UsageMetricsEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public UsageMetricsEntity setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public UsageMetricsEntity setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
    return this;
  }

  public UsageMetricsEventType getEventType() {
    return eventType;
  }

  public UsageMetricsEntity setEventType(final UsageMetricsEventType eventType) {
    this.eventType = eventType;
    return this;
  }

  public Long getEventValue() {
    return eventValue;
  }

  public UsageMetricsEntity setEventValue(final Long eventValue) {
    this.eventValue = eventValue;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public UsageMetricsEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public UsageMetricsEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, startTime, endTime, eventType, eventValue, tenantId, partitionId);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (UsageMetricsEntity) obj;
    return Objects.equals(id, that.id)
        && Objects.equals(startTime, that.startTime)
        && Objects.equals(endTime, that.endTime)
        && Objects.equals(eventType, that.eventType)
        && Objects.equals(eventValue, that.eventValue)
        && Objects.equals(tenantId, that.tenantId)
        && partitionId == that.partitionId;
  }

  @Override
  public String toString() {
    return "UsageMetricsEntity[id=%s, startTime=%s, endTime=%s, eventType=%s, eventValue=%d, tenantId=%s, partitionId=%d]"
        .formatted(id, startTime, endTime, eventType, eventValue, tenantId, partitionId);
  }
}
