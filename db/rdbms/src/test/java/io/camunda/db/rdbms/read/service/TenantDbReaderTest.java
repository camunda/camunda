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
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.search.query.TenantQuery;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantDbReaderTest {

  private final TenantMapper mapper = mock(TenantMapper.class);
  private final TenantDbReader reader = new TenantDbReader(mapper);

  @Test
  void shouldImmediatelyReturnEmptyResultWhenMemberIdsFilterIsEmpty() {
    // When
    final var result =
        reader.search(
            TenantQuery.of(
                b -> b.filter(f -> f.memberIds(Set.of()).childMemberType(EntityType.USER))),
            null);

    // Then
    assertThat(result.total()).isZero();
    verifyNoInteractions(mapper);
  }
}
