/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static java.util.Optional.ofNullable;

import io.camunda.db.rdbms.read.domain.UsageMetricsDbQuery;
import io.camunda.db.rdbms.read.mapper.UsageMetricEntityMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.search.clients.reader.UsageMetricsReader;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricsDbReader implements UsageMetricsReader {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricsDbReader.class);
  private final UsageMetricMapper usageMetricMapper;

  public UsageMetricsDbReader(final UsageMetricMapper usageMetricMapper) {
    this.usageMetricMapper = usageMetricMapper;
  }

  @Override
  public UsageMetricStatisticsEntity usageMetricStatistics(
      final UsageMetricsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Aggregate usage metrics statistics with {}", query);

    // If the tenant check is enabled and no tenant IDs are authorized, return an empty result
    if (resourceAccessChecks.tenantCheck().enabled()
        && resourceAccessChecks.getAuthorizedTenantIds().isEmpty()) {
      return new UsageMetricStatisticsEntity(0L, 0L, 0L, null);
    }

    final var dbQuery =
        UsageMetricsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds()));

    if (query.filter().withTenants()) {
      final var result = usageMetricMapper.usageMetricTenantsStatistics(dbQuery);
      return UsageMetricEntityMapper.toEntity(result);
    } else {
      final var result = usageMetricMapper.usageMetricStatistics(dbQuery);
      return new UsageMetricStatisticsEntity(
          ofNullable(result.rpi()).orElse(0L),
          ofNullable(result.edi()).orElse(0L),
          ofNullable(result.at()).orElse(0L),
          null);
    }
  }
}
