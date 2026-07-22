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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateMetadataMapperTest {

  @Test
  void shouldMapLatestSuccessfulAuditLog() {
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
    final var item = new Item("123");

    UpdateMetadataMapper.addUpdateMetadata(
        item,
        Item::key,
        USER_TASK,
        services,
        authentication,
        Item::setUpdatedBy,
        Item::setUpdatedAt);

    assertThat(item.updatedBy).isEqualTo("demo");
    assertThat(item.updatedAt).isEqualTo("2026-07-22T10:15:30.000Z");
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
  void shouldLeaveMetadataNullWhenAuditLookupFails() {
    final var services = mock(AuditLogServices.class);
    when(services.search(any(), any())).thenThrow(new RuntimeException("unavailable"));
    final var item = new Item("123");

    UpdateMetadataMapper.addUpdateMetadata(
        item,
        Item::key,
        USER_TASK,
        services,
        mock(CamundaAuthentication.class),
        Item::setUpdatedBy,
        Item::setUpdatedAt);

    assertThat(item.updatedBy).isNull();
    assertThat(item.updatedAt).isNull();
  }

  private static final class Item {
    private final String key;
    private String updatedBy;
    private String updatedAt;

    private Item(final String key) {
      this.key = key;
    }

    private String key() {
      return key;
    }

    private void setUpdatedBy(final String updatedBy) {
      this.updatedBy = updatedBy;
    }

    private void setUpdatedAt(final String updatedAt) {
      this.updatedAt = updatedAt;
    }
  }
}
