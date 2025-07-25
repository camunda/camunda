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

public class UsageMetricsTUEntity
    implements ExporterEntity<UsageMetricsTUEntity>,
        PartitionedEntity<UsageMetricsTUEntity>,
        TenantOwned {

  private String id;
  private OffsetDateTime eventTime;
  private Long assigneeHash;
  private String tenantId;
  private int partitionId;

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, eventTime, assigneeHash, partitionId);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UsageMetricsTUEntity that = (UsageMetricsTUEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(partitionId, that.partitionId)
        && Objects.equals(eventTime, that.eventTime)
        && Objects.equals(assigneeHash, that.assigneeHash);
  }

  @Override
  public String toString() {
    return "UsageMetricsTUEntity{"
        + "id='"
        + id
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", partitionId='"
        + partitionId
        + '\''
        + ", eventTime="
        + eventTime
        + '\''
        + ", assigneeHash='"
        + assigneeHash
        + '\''
        + '}';
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public UsageMetricsTUEntity setId(final String id) {
    this.id = id;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public UsageMetricsTUEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getEventTime() {
    return eventTime;
  }

  public UsageMetricsTUEntity setEventTime(final OffsetDateTime eventTime) {
    this.eventTime = eventTime;
    return this;
  }

  public long getAssigneeHash() {
    return assigneeHash;
  }

  public UsageMetricsTUEntity setAssigneeHash(final long assigneeHash) {
    this.assigneeHash = assigneeHash;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public UsageMetricsTUEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }
}
