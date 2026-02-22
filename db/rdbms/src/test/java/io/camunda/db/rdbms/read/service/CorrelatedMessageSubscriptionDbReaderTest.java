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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorrelatedMessageSubscriptionDbReaderTest {
  private final CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper =
      mock(CorrelatedMessageSubscriptionMapper.class);
  private final CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionDbReader =
      new CorrelatedMessageSubscriptionDbReader(
          correlatedMessageSubscriptionMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final CorrelatedMessageSubscriptionQuery query = CorrelatedMessageSubscriptionQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(Authorization.of(a -> a.readProcessInstance().read())),
            TenantCheck.disabled());

    final var items =
        correlatedMessageSubscriptionDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final CorrelatedMessageSubscriptionQuery query = CorrelatedMessageSubscriptionQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items =
        correlatedMessageSubscriptionDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyPageWhenPageSizeIsZero() {
    when(correlatedMessageSubscriptionMapper.count(any())).thenReturn(21L);

    final CorrelatedMessageSubscriptionQuery query =
        CorrelatedMessageSubscriptionQuery.of(b -> b.page(p -> p.size(0)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = correlatedMessageSubscriptionDbReader.search(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.items()).isEmpty();
    verify(correlatedMessageSubscriptionMapper, times(0)).search(any());
  }
}
