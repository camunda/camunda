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

import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableDbReaderTest {
  private final VariableMapper variableMapper = mock(VariableMapper.class);
  private final VariableDbReader variableDbReader = new VariableDbReader(variableMapper);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final VariableQuery query = VariableQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(Authorization.of(a -> a.readProcessInstance().read())),
            TenantCheck.disabled());

    final var items = variableDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final VariableQuery query = VariableQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items = variableDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }
}
