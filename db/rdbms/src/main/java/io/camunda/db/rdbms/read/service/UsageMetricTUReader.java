/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel.UsageMetricTUAssigneesStatisticsDbModel;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.filter.UsageMetricsFilter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricTUReader {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricTUReader.class);
  private final UsageMetricTUMapper usageMetricTUMapper;

  public UsageMetricTUReader(final UsageMetricTUMapper usageMetricTUMapper) {
    this.usageMetricTUMapper = usageMetricTUMapper;
  }

  public UsageMetricTUStatisticsEntity usageMetricTUStatistics(final UsageMetricsFilter filter) {
    LOG.trace("[RDBMS DB] Usage metrics assignees with {}", filter);

    final var result = usageMetricTUMapper.usageMetricTUAssigneesStatistics(filter);
    Map<String, Set<Long>> tenantAssigneesMap =
        result.stream()
            .collect(
                Collectors.groupingBy(
                    UsageMetricTUAssigneesStatisticsDbModel::tenantId,
                    Collectors.mapping(
                        UsageMetricTUAssigneesStatisticsDbModel::assigneeHash,
                        Collectors.toSet())));
    return new UsageMetricTUStatisticsEntity(tenantAssigneesMap);
  }
}
