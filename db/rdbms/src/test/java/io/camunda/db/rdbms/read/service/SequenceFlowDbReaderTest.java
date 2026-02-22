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

import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class SequenceFlowDbReaderTest {
  private final SequenceFlowMapper sequenceFlowMapper = mock(SequenceFlowMapper.class);
  private final SequenceFlowDbReader sequenceFlowDbReader =
      new SequenceFlowDbReader(sequenceFlowMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final SequenceFlowQuery query = SequenceFlowQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(Authorization.of(a -> a.processDefinition().read())),
            TenantCheck.disabled());

    final var items = sequenceFlowDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final SequenceFlowQuery query = SequenceFlowQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items = sequenceFlowDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }
}
