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

import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessInstanceDbReaderTest {
  private final ProcessInstanceMapper processInstanceMapper = mock(ProcessInstanceMapper.class);
  private final ProcessInstanceDbReader processInstanceDbReader =
      new ProcessInstanceDbReader(processInstanceMapper);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final ProcessInstanceQuery query = ProcessInstanceQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(Authorization.of(a -> a.readProcessInstance().read())),
            TenantCheck.disabled());

    final var items = processInstanceDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final ProcessInstanceQuery query = ProcessInstanceQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items = processInstanceDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }
}
