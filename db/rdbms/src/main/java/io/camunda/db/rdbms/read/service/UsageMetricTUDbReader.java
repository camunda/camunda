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
import io.camunda.search.clients.reader.UsageMetricsTUReader;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.query.UsageMetricsTUQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricTUDbReader implements UsageMetricsTUReader {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricTUDbReader.class);
  private final UsageMetricTUMapper usageMetricTUMapper;

  public UsageMetricTUDbReader(final UsageMetricTUMapper usageMetricTUMapper) {
    this.usageMetricTUMapper = usageMetricTUMapper;
  }

  @Override
  public UsageMetricTUStatisticsEntity usageMetricTUStatistics(
      final UsageMetricsTUQuery query, final ResourceAccessChecks access) {
    LOG.trace("[RDBMS DB] Usage metrics assignees with {}", query);
    final var filter = query.filter();

    final var tuStatistics = usageMetricTUMapper.usageMetricTUStatistics(filter);
    final var totalTu = ofNullable(tuStatistics.tu()).orElse(0L);
    if (filter.withTenants()) {
      final var result = usageMetricTUMapper.usageMetricTUTenantsStatistics(filter);
      return UsageMetricTUEntityMapper.toEntity(result, totalTu);
    } else {
      return new UsageMetricTUStatisticsEntity(totalTu, null);
    }
  }
}
