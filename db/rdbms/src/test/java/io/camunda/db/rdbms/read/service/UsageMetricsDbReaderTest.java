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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.domain.UsageMetricsDbQuery;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.write.domain.UsageMetricDbModel.UsageMetricStatisticsDbModel;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsageMetricsDbReaderTest {

  private final UsageMetricMapper usageMetricMapper = mock(UsageMetricMapper.class);
  private final UsageMetricsDbReader reader = new UsageMetricsDbReader(usageMetricMapper);

  @Test
  void shouldReturnEmptyResultWithoutUsageMetricPermission() {
    // given
    final var query = UsageMetricsQuery.of(b -> b);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(Authorization.of(a -> a.system().readUsageMetric())),
            TenantCheck.disabled());

    // when
    final var result = reader.usageMetricStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.totalRpi()).isZero();
    assertThat(result.totalEdi()).isZero();
    assertThat(result.at()).isZero();
    assertThat(result.tenants()).isEmpty();
    verifyNoInteractions(usageMetricMapper);
  }

  @Test
  void shouldReturnEmptyResultWhenTenantCheckIsEnabledWithoutTenants() {
    // given
    final var query = UsageMetricsQuery.of(b -> b);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    // when
    final var result = reader.usageMetricStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.totalRpi()).isZero();
    assertThat(result.tenants()).isEmpty();
    verifyNoInteractions(usageMetricMapper);
  }

  @Test
  void shouldPassAuthorizedTenantsToMapper() {
    // given
    final var query = UsageMetricsQuery.of(b -> b);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of("a")));
    when(usageMetricMapper.usageMetricStatistics(any()))
        .thenReturn(new UsageMetricStatisticsDbModel(1L, 2L, 1L));

    // when
    reader.usageMetricStatistics(query, resourceAccessChecks);

    // then
    final var captor = ArgumentCaptor.forClass(UsageMetricsDbQuery.class);
    verify(usageMetricMapper).usageMetricStatistics(captor.capture());
    assertThat(captor.getValue().authorizedTenantIds()).containsExactly("a");
  }
}
