/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.metrics;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class UsageMetricsEntity
    implements ExporterEntity<UsageMetricsEntity>,
        PartitionedEntity<UsageMetricsEntity>,
        TenantOwned {

  private String id;
  private OffsetDateTime eventTime;
  private UsageMetricsEventType eventType;
  private Long eventValue;
  private String tenantId;
  private int partitionId;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public UsageMetricsEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public OffsetDateTime getEventTime() {
    return eventTime;
  }

  public UsageMetricsEntity setEventTime(final OffsetDateTime eventTime) {
    this.eventTime = eventTime;
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
    return Objects.hash(id, eventTime, eventType, eventValue, tenantId, partitionId);
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
        && Objects.equals(eventTime, that.eventTime)
        && Objects.equals(eventType, that.eventType)
        && Objects.equals(eventValue, that.eventValue)
        && Objects.equals(tenantId, that.tenantId)
        && partitionId == that.partitionId;
  }

  @Override
  public String toString() {
    return "UsageMetricsEntity[id=%s, eventTime=%s, eventType=%s, eventValue=%d, tenantId=%s, partitionId=%d]"
        .formatted(id, eventTime, eventType, eventValue, tenantId, partitionId);
  }
}
