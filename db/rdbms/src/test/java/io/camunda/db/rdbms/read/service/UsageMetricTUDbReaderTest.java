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

import io.camunda.db.rdbms.read.domain.UsageMetricsTUDbQuery;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel.UsageMetricTUStatisticsDbModel;
import io.camunda.search.query.UsageMetricsTUQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsageMetricTUDbReaderTest {

  private final UsageMetricTUMapper usageMetricTUMapper = mock(UsageMetricTUMapper.class);
  private final UsageMetricTUDbReader reader = new UsageMetricTUDbReader(usageMetricTUMapper);

  @Test
  void shouldReturnEmptyResultWithoutUsageMetricPermission() {
    // given
    final var query = UsageMetricsTUQuery.of(b -> b.filter(f -> f.withTenants(true)));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(Authorization.of(a -> a.system().readUsageMetric())),
            TenantCheck.disabled());

    // when
    final var result = reader.usageMetricTUStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.totalTu()).isZero();
    assertThat(result.tenants()).isEmpty();
    verifyNoInteractions(usageMetricTUMapper);
  }

  @Test
  void shouldReturnEmptyResultWhenTenantCheckIsEnabledWithoutTenants() {
    // given
    final var query = UsageMetricsTUQuery.of(b -> b);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    // when
    final var result = reader.usageMetricTUStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.totalTu()).isZero();
    assertThat(result.tenants()).isEmpty();
    verifyNoInteractions(usageMetricTUMapper);
  }

  @Test
  void shouldPassAuthorizedTenantsToMapper() {
    // given
    final var query = UsageMetricsTUQuery.of(b -> b);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of("a")));
    when(usageMetricTUMapper.usageMetricTUStatistics(any()))
        .thenReturn(new UsageMetricTUStatisticsDbModel(1L));

    // when
    reader.usageMetricTUStatistics(query, resourceAccessChecks);

    // then
    final var captor = ArgumentCaptor.forClass(UsageMetricsTUDbQuery.class);
    verify(usageMetricTUMapper).usageMetricTUStatistics(captor.capture());
    assertThat(captor.getValue().authorizedTenantIds()).containsExactly("a");
  }
}
