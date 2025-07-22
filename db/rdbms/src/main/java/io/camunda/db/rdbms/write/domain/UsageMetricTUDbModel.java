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

public record UsageMetricTUDbModel(
    long key, OffsetDateTime eventTime, String tenantId, long assigneeHash, int partitionId) {

  public String getId() {
    return key + "_" + tenantId;
  }

  public record UsageMetricTUStatisticsDbModel(Long tu) {}

  public record UsageMetricTUTenantStatisticsDbModel(String tenantId, Long tu) {}

  public static class Builder implements ObjectBuilder<UsageMetricTUDbModel> {

    private long key;
    private OffsetDateTime eventTime;
    private String tenantId;
    private long assigneeHash;
    private int partitionId;

    public Builder key(final long key) {
      this.key = key;
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

    public Builder assigneeHash(final long assigneeHash) {
      this.assigneeHash = assigneeHash;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public UsageMetricTUDbModel build() {
      return new UsageMetricTUDbModel(key, eventTime, tenantId, assigneeHash, partitionId);
    }
  }
}
