/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.USER_TASK;
import static io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory.USER_TASKS;
import static io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.SUCCESS;
import static io.camunda.search.entities.AuditLogEntity.AuditLogOperationType.UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.AuditLogServices;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateMetadataMapperTest {

  @Test
  void shouldResolveLatestSuccessfulAuditLog() {
    final var services = mock(AuditLogServices.class);
    final var authentication = mock(CamundaAuthentication.class);
    final var auditLog =
        new AuditLogEntity.Builder()
            .auditLogKey("1-2")
            .entityKey("123")
            .entityType(USER_TASK)
            .operationType(UPDATE)
            .timestamp(OffsetDateTime.parse("2026-07-22T10:15:30Z"))
            .actorId("demo")
            .result(SUCCESS)
            .category(USER_TASKS)
            .build();
    when(services.search(any(), same(authentication))).thenReturn(SearchQueryResult.of(auditLog));

    final var metadata =
        UpdateMetadataMapper.resolve("123", key -> key, USER_TASK, services, authentication);

    assertThat(metadata.updatedBy()).isEqualTo("demo");
    assertThat(metadata.updatedAt()).isEqualTo("2026-07-22T10:15:30.000Z");
    final var queryCaptor = ArgumentCaptor.forClass(AuditLogQuery.class);
    verify(services).search(queryCaptor.capture(), same(authentication));
    final var query = queryCaptor.getValue();
    assertThat(query.filter().entityKeyOperations())
        .containsExactly(io.camunda.search.filter.Operation.eq("123"));
    assertThat(query.filter().entityTypeOperations())
        .containsExactly(io.camunda.search.filter.Operation.eq("USER_TASK"));
    assertThat(query.filter().resultOperations())
        .containsExactly(io.camunda.search.filter.Operation.eq("SUCCESS"));
    assertThat(query.sort().orderings())
        .containsExactly(
            new io.camunda.search.sort.SortOption.FieldSorting(
                "timestamp", io.camunda.search.sort.SortOrder.DESC));
    assertThat(query.page().size()).isEqualTo(1);
  }

  @Test
  void shouldReturnEmptyMetadataWhenAuditLookupFails() {
    final var services = mock(AuditLogServices.class);
    when(services.search(any(), any())).thenThrow(new RuntimeException("unavailable"));

    final var metadata =
        UpdateMetadataMapper.resolve(
            "123", key -> key, USER_TASK, services, mock(CamundaAuthentication.class));

    assertThat(metadata.updatedBy()).isNull();
    assertThat(metadata.updatedAt()).isNull();
  }

  @Test
  void shouldFallBackToCreationTimestampWhenNoAuditLogEntryExists() {
    final var services = mock(AuditLogServices.class);
    when(services.search(any(), any())).thenReturn(SearchQueryResult.empty());

    final var metadata =
        UpdateMetadataMapper.resolve(
            "123",
            key -> key,
            USER_TASK,
            services,
            mock(CamundaAuthentication.class),
            key -> "2026-01-01T00:00:00.000Z");

    assertThat(metadata.updatedBy()).isNull();
    assertThat(metadata.updatedAt()).isEqualTo("2026-01-01T00:00:00.000Z");
  }

  @Test
  void shouldPreferAuditLogEntryOverFallbackWhenBothAvailable() {
    final var services = mock(AuditLogServices.class);
    final var auditLog =
        new AuditLogEntity.Builder()
            .auditLogKey("1-2")
            .entityKey("123")
            .entityType(USER_TASK)
            .operationType(UPDATE)
            .timestamp(OffsetDateTime.parse("2026-07-22T10:15:30Z"))
            .actorId("demo")
            .result(SUCCESS)
            .category(USER_TASKS)
            .build();
    when(services.search(any(), any())).thenReturn(SearchQueryResult.of(auditLog));

    final var metadata =
        UpdateMetadataMapper.resolve(
            "123",
            key -> key,
            USER_TASK,
            services,
            mock(CamundaAuthentication.class),
            key -> "2026-01-01T00:00:00.000Z");

    assertThat(metadata.updatedBy()).isEqualTo("demo");
    assertThat(metadata.updatedAt()).isEqualTo("2026-07-22T10:15:30.000Z");
  }

  @Test
  void shouldResolveAllForMultipleItems() {
    final var services = mock(AuditLogServices.class);
    final var authentication = mock(CamundaAuthentication.class);
    final var auditLogFor1 =
        new AuditLogEntity.Builder()
            .auditLogKey("1-2")
            .entityKey("1")
            .entityType(USER_TASK)
            .operationType(UPDATE)
            .timestamp(OffsetDateTime.parse("2026-07-22T10:15:30Z"))
            .actorId("demo")
            .result(SUCCESS)
            .category(USER_TASKS)
            .build();
    when(services.search(any(), same(authentication)))
        .thenReturn(SearchQueryResult.of(auditLogFor1))
        .thenReturn(SearchQueryResult.empty());

    final var metadataByKey =
        UpdateMetadataMapper.resolveAll(
            List.of("1", "2"), key -> key, USER_TASK, services, authentication);

    assertThat(metadataByKey.get("1").updatedBy()).isEqualTo("demo");
    assertThat(metadataByKey).doesNotContainKey("2");
  }
}
