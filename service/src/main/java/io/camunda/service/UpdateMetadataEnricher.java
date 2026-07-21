/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.exception.ServiceException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UpdateMetadataEnricher {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateMetadataEnricher.class);

  private final @Nullable AuditLogServices auditLogServices;

  public UpdateMetadataEnricher(final @Nullable AuditLogServices auditLogServices) {
    this.auditLogServices = auditLogServices;
  }

  public <T> T enrichItem(
      final T item,
      final AuditLogEntityType entityType,
      final Function<T, String> entityKey,
      final BiFunction<T, UpdateMetadata, T> withUpdateMetadata,
      final CamundaAuthentication authentication) {
    return enrichPage(
            new SearchQueryResult<>(1, false, List.of(item), null, null),
            entityType,
            entityKey,
            withUpdateMetadata,
            authentication)
        .items()
        .getFirst();
  }

  public <T> SearchQueryResult<T> enrichPage(
      final SearchQueryResult<T> result,
      final AuditLogEntityType entityType,
      final Function<T, String> entityKey,
      final BiFunction<T, UpdateMetadata, T> withUpdateMetadata,
      final CamundaAuthentication authentication) {
    if (auditLogServices == null || result.items().isEmpty()) {
      return result;
    }

    final List<String> entityKeys = result.items().stream().map(entityKey).distinct().toList();
    final Map<String, AuditLogEntity> auditLogs;
    try {
      auditLogs =
          auditLogServices.latestSuccessfulByEntityKeys(entityType, entityKeys, authentication);
    } catch (final ServiceException ignored) {
      LOGGER.warn("Unable to retrieve supplemental update metadata; returning entities without it");
      return result.withItems(
          result.items().stream()
              .map(item -> withUpdateMetadata.apply(item, UpdateMetadata.EMPTY))
              .toList());
    }

    return result.withItems(
        result.items().stream()
            .map(
                item -> {
                  final var auditLog = auditLogs.get(entityKey.apply(item));
                  return withUpdateMetadata.apply(
                      item,
                      auditLog == null
                          ? UpdateMetadata.EMPTY
                          : new UpdateMetadata(auditLog.actorId(), auditLog.timestamp()));
                })
            .toList());
  }

  public record UpdateMetadata(@Nullable String updatedBy, @Nullable OffsetDateTime updatedAt) {
    private static final UpdateMetadata EMPTY = new UpdateMetadata(null, null);
  }
}
