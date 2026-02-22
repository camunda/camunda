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

import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import org.junit.jupiter.api.Test;

class DecisionInstanceDbReaderTest {

  private final DecisionInstanceMapper decisionInstanceMapper = mock(DecisionInstanceMapper.class);
  private final DecisionInstanceDbReader decisionInstanceDbReader =
      new DecisionInstanceDbReader(decisionInstanceMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyPageWhenPageSizeIsZero() {
    when(decisionInstanceMapper.count(any())).thenReturn(21L);

    final DecisionInstanceQuery query = DecisionInstanceQuery.of(b -> b.page(p -> p.size(0)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = decisionInstanceDbReader.search(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.items()).isEmpty();
    verify(decisionInstanceMapper, times(0)).search(any());
  }
}
