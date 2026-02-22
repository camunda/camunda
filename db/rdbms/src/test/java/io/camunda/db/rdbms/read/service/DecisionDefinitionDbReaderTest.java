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

import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionDefinitionDbReaderTest {
  private final DecisionDefinitionMapper decisionDefinitionMapper =
      mock(DecisionDefinitionMapper.class);
  private final DecisionDefinitionDbReader decisionDefinitionDbReader =
      new DecisionDefinitionDbReader(
          decisionDefinitionMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyResultWhenAuthorizedResourceIdsIsNull() {
    final DecisionDefinitionQuery query = DecisionDefinitionQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(Authorization.of(a -> a.decisionDefinition().read())),
            TenantCheck.disabled());

    final var items = decisionDefinitionDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyResultWhenAuthorizedTenantIdsIsNull() {
    final DecisionDefinitionQuery query = DecisionDefinitionQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items = decisionDefinitionDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyPageWhenPageSizeIsZero() {
    when(decisionDefinitionMapper.count(any())).thenReturn(21L);

    final DecisionDefinitionQuery query = DecisionDefinitionQuery.of(b -> b.page(p -> p.size(0)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = decisionDefinitionDbReader.search(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.items()).isEmpty();
    verify(decisionDefinitionMapper, times(0)).search(any());
  }
}
