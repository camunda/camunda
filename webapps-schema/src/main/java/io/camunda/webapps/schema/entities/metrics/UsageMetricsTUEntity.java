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

public class UsageMetricsTUEntity
    implements ExporterEntity<UsageMetricsTUEntity>,
        PartitionedEntity<UsageMetricsTUEntity>,
        TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private OffsetDateTime startTime;
  @BeforeVersion880 private OffsetDateTime endTime;
  @BeforeVersion880 private Long assigneeHash;
  @BeforeVersion880 private String tenantId;
  @BeforeVersion880 private int partitionId;

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, startTime, endTime, assigneeHash, partitionId);
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
        && Objects.equals(startTime, that.startTime)
        && Objects.equals(endTime, that.endTime)
        && Objects.equals(assigneeHash, that.assigneeHash);
  }

  @Override
  public String toString() {
    return "UsageMetricsTUEntity{id='%s', startTime=%s, endTime=%s, assigneeHash=%d, tenantId='%s', partitionId=%d}"
        .formatted(id, startTime, endTime, assigneeHash, tenantId, partitionId);
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

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public UsageMetricsTUEntity setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public UsageMetricsTUEntity setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
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
