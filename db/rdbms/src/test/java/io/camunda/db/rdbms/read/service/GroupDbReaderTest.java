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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.search.query.GroupQuery;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroupDbReaderTest {

  private final GroupMapper mapper = mock(GroupMapper.class);
  private final GroupDbReader reader =
      new GroupDbReader(mapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldImmediatelyReturnEmptyResultWhenMemberIdsFilterIsEmpty() {
    // When
    final var result =
        reader.search(
            GroupQuery.of(
                b -> b.filter(f -> f.memberIds(Set.of()).childMemberType(EntityType.USER))),
            null);

    // Then
    assertThat(result.total()).isZero();
    verifyNoInteractions(mapper);
  }

  @Test
  void shouldReturnEmptyPageWhenPageSizeIsZero() {
    when(mapper.count(any())).thenReturn(21L);

    final GroupQuery query = GroupQuery.of(b -> b.page(p -> p.size(0)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = reader.search(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.items()).isEmpty();
    verify(mapper, times(0)).search(any());
  }

  @Test
  void shouldMapNullStringFieldsToEmptyStrings() {
    // Given: a GroupDbModel with null groupId and name (simulates Oracle NULL for empty strings)
    final var model = new GroupDbModel.Builder().groupKey(1L).build();
    when(mapper.count(any())).thenReturn(1L);
    when(mapper.search(any())).thenReturn(List.of(model));

    final GroupQuery query = GroupQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // When
    final var result = reader.search(query, resourceAccessChecks);

    // Then: required string fields should be empty strings, not null
    assertThat(result.items()).hasSize(1);
    final var entity = result.items().getFirst();
    assertThat(entity.groupId()).isEqualTo("");
    assertThat(entity.name()).isEqualTo("");
  }
}
