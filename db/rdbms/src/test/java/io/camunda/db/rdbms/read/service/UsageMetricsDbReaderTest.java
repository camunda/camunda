/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageMetricsDbReaderTest {
  private final UsageMetricMapper usageMetricsMapper = mock(UsageMetricMapper.class);
  private final UsageMetricsDbReader usageMetricsDbReader =
      new UsageMetricsDbReader(usageMetricsMapper);

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final UsageMetricsQuery query = UsageMetricsQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var statistics = usageMetricsDbReader.usageMetricStatistics(query, resourceAccessChecks);
    assertThat(statistics.totalEdi()).isEqualTo(0);
    assertThat(statistics.totalRpi()).isEqualTo(0);
    assertThat(statistics.at()).isEqualTo(0);
    assertThat(statistics.tenants()).isNull();
  }
}
