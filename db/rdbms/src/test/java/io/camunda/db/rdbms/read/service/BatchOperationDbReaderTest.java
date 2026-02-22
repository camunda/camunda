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

import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchOperationDbReaderTest {
  private final BatchOperationMapper batchOperationMapper = mock(BatchOperationMapper.class);
  private final BatchOperationDbReader batchOperationDbReader =
      new BatchOperationDbReader(batchOperationMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyResultWhenAuthorizedResourceIdsIsNull() {
    final BatchOperationQuery query = BatchOperationQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(Authorization.of(a -> a.batchOperation().read())),
            TenantCheck.disabled());

    final var items = batchOperationDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyResultWhenAuthorizedTenantIdsIsNull() {
    final BatchOperationQuery query = BatchOperationQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items = batchOperationDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyPageWhenPageSizeIsZero() {
    when(batchOperationMapper.count(any())).thenReturn(21L);

    final BatchOperationQuery query = BatchOperationQuery.of(b -> b.page(p -> p.size(0)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = batchOperationDbReader.search(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.items()).isEmpty();
    verify(batchOperationMapper, times(0)).search(any());
  }
}
