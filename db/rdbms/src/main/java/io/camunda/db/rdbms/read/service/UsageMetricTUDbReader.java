/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static java.util.Optional.ofNullable;

import io.camunda.db.rdbms.read.mapper.UsageMetricTUEntityMapper;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.filter.UsageMetricsFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricTUDbReader {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricTUDbReader.class);
  private final UsageMetricTUMapper usageMetricTUMapper;

  public UsageMetricTUDbReader(final UsageMetricTUMapper usageMetricTUMapper) {
    this.usageMetricTUMapper = usageMetricTUMapper;
  }

  public UsageMetricTUStatisticsEntity usageMetricTUStatistics(final UsageMetricsFilter filter) {
    LOG.trace("[RDBMS DB] Usage metrics assignees with {}", filter);

    if (filter.withTenants()) {
      final var result = usageMetricTUMapper.usageMetricTUTenantsStatistics(filter);
      return UsageMetricTUEntityMapper.toEntity(result);
    } else {
      final var result = usageMetricTUMapper.usageMetricTUStatistics(filter);
      return new UsageMetricTUStatisticsEntity(ofNullable(result.tu()).orElse(0L), null);
    }
  }
}
