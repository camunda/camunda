/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static java.util.Optional.ofNullable;

import io.camunda.db.rdbms.read.mapper.UsageMetricEntityMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.search.clients.reader.UsageMetricsReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricsEntity;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UsageMetricsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricsDbReader extends AbstractEntityReader<UsageMetricsEntity>
    implements UsageMetricsReader {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricsDbReader.class);
  private final UsageMetricMapper usageMetricMapper;

  public UsageMetricsDbReader(final UsageMetricMapper usageMetricMapper) {
    super(null);
    this.usageMetricMapper = usageMetricMapper;
  }

  @Override
  public UsageMetricsEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("UsageMetrics Reader not implemented yet.");
  }

  @Override
  public SearchQueryResult<UsageMetricsEntity> search(
      final UsageMetricsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("UsageMetrics Reader not implemented yet.");
  }

  public UsageMetricStatisticsEntity usageMetricStatistics(final UsageMetricsFilter filter) {
    LOG.trace("[RDBMS DB] Aggregate usage metrics statistics with {}", filter);

    if (filter.withTenants()) {
      final var result = usageMetricMapper.usageMetricTenantsStatistics(filter);
      return UsageMetricEntityMapper.toEntity(result);
    } else {
      final var result = usageMetricMapper.usageMetricStatistics(filter);
      return new UsageMetricStatisticsEntity(
          ofNullable(result.rpi()).orElse(0L),
          ofNullable(result.edi()).orElse(0L),
          ofNullable(result.at()).orElse(0L),
          null);
    }
  }
}
