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

import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.search.query.IncidentQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentDbReaderTest {
  private final IncidentMapper incidentMapper = mock(IncidentMapper.class);
  private final IncidentDbReader incidentDbReader = new IncidentDbReader(incidentMapper);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final IncidentQuery query = IncidentQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(a -> a.processDefinition().readProcessInstance())),
            TenantCheck.disabled());

    final var items = incidentDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final IncidentQuery query = IncidentQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items = incidentDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }
}
