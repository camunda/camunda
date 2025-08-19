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

import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.search.query.RoleQuery;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleDbReaderTest {

  private final RoleMapper roleMapper = mock(RoleMapper.class);
  private final RoleDbReader reader = new RoleDbReader(roleMapper);

  @Test
  void shouldImmediatelyReturnEmptyResultWhenMemberIdsFilterIsEmpty() {
    // When
    final var result =
        reader.search(
            RoleQuery.of(
                b -> b.filter(f -> f.memberIds(Set.of()).childMemberType(EntityType.USER))),
            null);

    // Then
    assertThat(result.total()).isZero();
    verifyNoInteractions(roleMapper);
  }

  @Test
  void shouldImmediatelyReturnEmptyResultWhenRoleIdsFilterIsEmpty() {
    // When
    final var result = reader.search(RoleQuery.of(b -> b.filter(f -> f.roleIds(Set.of()))), null);

    // Then
    assertThat(result.total()).isZero();
    verifyNoInteractions(roleMapper);
  }
}
