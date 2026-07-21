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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.domain.LatestAuditLogDbQuery;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditLogDbReaderTest {
  private final AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
  private final AuditLogDbReader auditLogDbReader =
      new AuditLogDbReader(auditLogMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final AuditLogQuery query = AuditLogQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(a -> a.readProcessInstance().read())),
            TenantCheck.disabled());

    final var items = auditLogDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final AuditLogQuery query = AuditLogQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items = auditLogDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyPageWhenPageSizeIsZero() {
    when(auditLogMapper.count(any())).thenReturn(21L);

    final AuditLogQuery query = AuditLogQuery.of(b -> b.page(p -> p.size(0)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = auditLogDbReader.search(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.items()).isEmpty();
    verify(auditLogMapper, times(0)).search(any());
  }

  @Test
  void shouldReturnEmptyLatestAuditLogsForEmptyEntityKeys() {
    final var result =
        auditLogDbReader.searchLatestSuccessfulByEntityKeys(
            AuditLogEntityType.USER_TASK, List.of(), ResourceAccessChecks.disabled());

    assertThat(result).isEmpty();
    verify(auditLogMapper, never()).searchLatestSuccessfulByEntityKeys(any());
  }

  @Test
  void shouldReturnEmptyLatestAuditLogsWhenNoResourceAccess() {
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(a -> a.readProcessInstance().read())),
            TenantCheck.disabled());

    final var result =
        auditLogDbReader.searchLatestSuccessfulByEntityKeys(
            AuditLogEntityType.USER_TASK, List.of("1"), resourceAccessChecks);

    assertThat(result).isEmpty();
    verify(auditLogMapper, never()).searchLatestSuccessfulByEntityKeys(any());
  }

  @Test
  void shouldDeduplicateKeysAndMapLatestAuditLogs() {
    final var dbModel =
        new AuditLogDbModel.Builder()
            .auditLogKey("1-2")
            .entityKey("entity-1")
            .entityType(AuditLogEntityType.USER_TASK)
            .operationType(AuditLogOperationType.UPDATE)
            .timestamp(OffsetDateTime.parse("2026-07-21T12:00:00Z"))
            .result(AuditLogOperationResult.SUCCESS)
            .category(AuditLogOperationCategory.USER_TASKS)
            .build();
    when(auditLogMapper.searchLatestSuccessfulByEntityKeys(any())).thenReturn(List.of(dbModel));

    final var result =
        auditLogDbReader.searchLatestSuccessfulByEntityKeys(
            AuditLogEntityType.USER_TASK,
            List.of("entity-1", "entity-2", "entity-1"),
            ResourceAccessChecks.of(
                AuthorizationCheck.disabled(), TenantCheck.enabled(List.of("tenant-1"))));

    assertThat(result)
        .singleElement()
        .satisfies(entity -> assertThat(entity.auditLogKey()).isEqualTo("1-2"));
    final var queryCaptor = ArgumentCaptor.forClass(LatestAuditLogDbQuery.class);
    verify(auditLogMapper).searchLatestSuccessfulByEntityKeys(queryCaptor.capture());
    assertThat(queryCaptor.getValue().entityType()).isEqualTo(AuditLogEntityType.USER_TASK);
    assertThat(queryCaptor.getValue().entityKeys()).containsExactly("entity-1", "entity-2");
    assertThat(queryCaptor.getValue().tenantCheckEnabled()).isTrue();
    assertThat(queryCaptor.getValue().authorizedTenantIds()).containsExactly("tenant-1");
    assertThat(queryCaptor.getValue().authorizationFilter().hasCategoryWildcard()).isTrue();
  }

  @Test
  void shouldChunkLargeKeyListsAndMergeOneResultPerKey() {
    final var entityKeys = IntStream.range(0, 10_000).mapToObj(index -> "entity-" + index).toList();
    when(auditLogMapper.searchLatestSuccessfulByEntityKeys(any()))
        .thenAnswer(
            invocation -> {
              final LatestAuditLogDbQuery query = invocation.getArgument(0);
              return query.entityKeys().stream().map(AuditLogDbReaderTest::auditLog).toList();
            });

    final var result =
        auditLogDbReader.searchLatestSuccessfulByEntityKeys(
            AuditLogEntityType.USER_TASK, entityKeys, ResourceAccessChecks.disabled());

    assertThat(result)
        .extracting(entity -> entity.entityKey())
        .containsExactlyElementsOf(entityKeys);
    final var queryCaptor = ArgumentCaptor.forClass(LatestAuditLogDbQuery.class);
    verify(auditLogMapper, times(12)).searchLatestSuccessfulByEntityKeys(queryCaptor.capture());
    assertThat(queryCaptor.getAllValues())
        .allSatisfy(
            query -> {
              assertThat(query.entityKeys()).hasSizeLessThanOrEqualTo(900);
              assertThat(query.tenantCheckEnabled()).isFalse();
            });
    assertThat(queryCaptor.getAllValues())
        .extracting(LatestAuditLogDbQuery::entityKeys)
        .flatMap(keys -> keys)
        .containsExactlyElementsOf(entityKeys);
  }

  private static AuditLogDbModel auditLog(final String entityKey) {
    return new AuditLogDbModel.Builder()
        .auditLogKey(entityKey)
        .entityKey(entityKey)
        .entityType(AuditLogEntityType.USER_TASK)
        .operationType(AuditLogOperationType.UPDATE)
        .timestamp(OffsetDateTime.parse("2026-07-21T12:00:00Z"))
        .result(AuditLogOperationResult.SUCCESS)
        .category(AuditLogOperationCategory.USER_TASKS)
        .build();
  }
}
