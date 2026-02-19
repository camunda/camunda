/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel.UsageMetricTUStatisticsDbModel;
import io.camunda.search.query.UsageMetricsTUQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import org.junit.jupiter.api.Test;

class UsageMetricTUDbReaderTest {
  private final UsageMetricTUMapper usageMetricTUMapper = mock(UsageMetricTUMapper.class);
  private final UsageMetricTUDbReader reader = new UsageMetricTUDbReader(usageMetricTUMapper);

  @Test
  void shouldReturnStatisticsWithoutTenants() {
    final var tuStatistics = new UsageMetricTUStatisticsDbModel(100L);
    when(usageMetricTUMapper.usageMetricTUStatistics(any())).thenReturn(tuStatistics);

    final UsageMetricsTUQuery query = UsageMetricsTUQuery.of(b -> b.filter(f -> f));
    final ResourceAccessChecks resourceAccessChecks = ResourceAccessChecks.disabled();

    final var result = reader.usageMetricTUStatistics(query, resourceAccessChecks);

    assertThat(result.totalTu()).isEqualTo(100L);
    assertThat(result.tenants()).isNotNull().isEmpty();
    verify(usageMetricTUMapper).usageMetricTUStatistics(any());
  }
}
