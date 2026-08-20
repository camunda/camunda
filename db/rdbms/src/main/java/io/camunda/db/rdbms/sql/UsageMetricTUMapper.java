/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel.UsageMetricTUStatisticsDbModel;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel.UsageMetricTUTenantStatisticsDbModel;
import io.camunda.search.filter.UsageMetricsTUFilter;
import java.util.List;

public interface UsageMetricTUMapper {

  List<UsageMetricTUTenantStatisticsDbModel> usageMetricTUTenantsStatistics(
      final UsageMetricsTUFilter filter);

  UsageMetricTUStatisticsDbModel usageMetricTUStatistics(final UsageMetricsTUFilter filter);

  int cleanupMetrics(CleanupHistoryDto dto);
}
