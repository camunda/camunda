/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class UpdateMetadataEnricherTest {

  private final AuditLogServices auditLogServices = mock(AuditLogServices.class);
  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
  private final UpdateMetadataEnricher enricher = new UpdateMetadataEnricher(auditLogServices);

  @Test
  void shouldMapMetadataAndLeaveMissingResultNullWithOneBulkLookup() {
    final var timestamp = OffsetDateTime.parse("2026-07-21T12:34:56Z");
    final var auditLog = mock(AuditLogEntity.class);
    when(auditLog.actorId()).thenReturn("demo");
    when(auditLog.timestamp()).thenReturn(timestamp);
    when(auditLogServices.latestSuccessfulByEntityKeys(
            AuditLogEntityType.VARIABLE, List.of("1", "2"), authentication))
        .thenReturn(Map.of("1", auditLog));

    final var result =
        enricher.enrichPage(
            SearchQueryResult.of(
                new Item("1", null, null), new Item("2", "stale", OffsetDateTime.MIN)),
            AuditLogEntityType.VARIABLE,
            Item::key,
            (item, metadata) -> item.withUpdateMetadata(metadata.updatedBy(), metadata.updatedAt()),
            authentication);

    assertThat(result.items())
        .containsExactly(new Item("1", "demo", timestamp), new Item("2", null, null));
    verify(auditLogServices)
        .latestSuccessfulByEntityKeys(
            AuditLogEntityType.VARIABLE, List.of("1", "2"), authentication);
  }

  @Test
  void shouldEnrichSingleItem() {
    final var timestamp = OffsetDateTime.parse("2026-07-21T12:34:56Z");
    final var auditLog = mock(AuditLogEntity.class);
    when(auditLog.actorId()).thenReturn("demo");
    when(auditLog.timestamp()).thenReturn(timestamp);
    when(auditLogServices.latestSuccessfulByEntityKeys(
            AuditLogEntityType.INCIDENT, List.of("3"), authentication))
        .thenReturn(Map.of("3", auditLog));

    final var result =
        enricher.enrichItem(
            new Item("3", null, null),
            AuditLogEntityType.INCIDENT,
            Item::key,
            (item, metadata) -> item.withUpdateMetadata(metadata.updatedBy(), metadata.updatedAt()),
            authentication);

    assertThat(result).isEqualTo(new Item("3", "demo", timestamp));
  }

  @Test
  void shouldReturnItemsWithNullMetadataWhenAuditSearchFails() {
    when(auditLogServices.latestSuccessfulByEntityKeys(
            AuditLogEntityType.VARIABLE, List.of("1"), authentication))
        .thenThrow(new ServiceException("audit search failed", Status.INTERNAL));

    final var result =
        enricher.enrichPage(
            SearchQueryResult.of(new Item("1", "stale", OffsetDateTime.MIN)),
            AuditLogEntityType.VARIABLE,
            Item::key,
            (item, metadata) -> item.withUpdateMetadata(metadata.updatedBy(), metadata.updatedAt()),
            authentication);

    assertThat(result.items()).containsExactly(new Item("1", null, null));
  }

  @Test
  void shouldReturnItemWithNullMetadataWhenNoAuditEntryExists() {
    when(auditLogServices.latestSuccessfulByEntityKeys(
            AuditLogEntityType.INCIDENT, List.of("4"), authentication))
        .thenReturn(Map.of());

    final var result =
        enricher.enrichItem(
            new Item("4", "stale", OffsetDateTime.MIN),
            AuditLogEntityType.INCIDENT,
            Item::key,
            (item, metadata) -> item.withUpdateMetadata(metadata.updatedBy(), metadata.updatedAt()),
            authentication);

    assertThat(result).isEqualTo(new Item("4", null, null));
  }

  private record Item(String key, String updatedBy, OffsetDateTime updatedAt) {
    Item withUpdateMetadata(final String newUpdatedBy, final OffsetDateTime newUpdatedAt) {
      return new Item(key, newUpdatedBy, newUpdatedAt);
    }
  }
}
