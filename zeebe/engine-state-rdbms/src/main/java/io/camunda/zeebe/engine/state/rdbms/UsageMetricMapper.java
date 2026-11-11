/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.util.List;
import org.apache.ibatis.annotations.Param;

interface UsageMetricMapper {

  /** The committed usage metrics of the active bucket. */
  List<UsageMetricRow> selectUsageMetrics(@Param("partitionId") int partitionId);

  /**
   * A usage metric is identified by its type, the tenant and, for the unique task users, the hash
   * of the assignee; the counters have no assignee and use 0.
   */
  record UsageMetricKey(short metricType, String tenantId, long assigneeHash) {}

  /**
   * The counter of a tenant's process instances or decision evaluations, or a unique task user of a
   * tenant, which counts once.
   */
  record UsageMetricRow(short metricType, String tenantId, long assigneeHash, long metricCount) {

    UsageMetricKey key() {
      return new UsageMetricKey(metricType, tenantId, assigneeHash);
    }

    UsageMetricRow incremented() {
      return new UsageMetricRow(metricType, tenantId, assigneeHash, metricCount + 1);
    }
  }
}
