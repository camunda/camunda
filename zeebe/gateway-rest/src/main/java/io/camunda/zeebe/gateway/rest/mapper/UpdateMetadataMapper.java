/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.SUCCESS;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.AuditLogServices;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves the {@code updatedBy}/{@code updatedAt} REST response metadata from the audit log, ahead
 * of constructing the (immutable, required-field) response objects.
 */
public final class UpdateMetadataMapper {

  private UpdateMetadataMapper() {}

  public static <T> ResolvedMetadata resolve(
      final T item,
      final Function<T, String> entityKeyExtractor,
      final AuditLogEntityType entityType,
      final AuditLogServices auditLogServices,
      final CamundaAuthentication authentication) {
    return resolve(item, entityKeyExtractor, entityType, auditLogServices, authentication, null);
  }

  /**
   * Same as {@link #resolve(Object, Function, AuditLogEntityType, AuditLogServices,
   * CamundaAuthentication)}, but falls back to {@code creationTimestampFallback} for {@code
   * updatedAt} when the entity has no audit log entry at all (e.g. it was never explicitly updated
   * after creation, and its creation is not itself an audited action).
   */
  public static <T> ResolvedMetadata resolve(
      final T item,
      final Function<T, String> entityKeyExtractor,
      final AuditLogEntityType entityType,
      final AuditLogServices auditLogServices,
      final CamundaAuthentication authentication,
      final Function<T, String> creationTimestampFallback) {
    return resolveAll(
            List.of(item),
            entityKeyExtractor,
            entityType,
            auditLogServices,
            authentication,
            creationTimestampFallback)
        .getOrDefault(entityKeyExtractor.apply(item), ResolvedMetadata.EMPTY);
  }

  public static <T> Map<String, ResolvedMetadata> resolveAll(
      final Collection<T> items,
      final Function<T, String> entityKeyExtractor,
      final AuditLogEntityType entityType,
      final AuditLogServices auditLogServices,
      final CamundaAuthentication authentication) {
    return resolveAll(
        items, entityKeyExtractor, entityType, auditLogServices, authentication, null);
  }

  public static <T> Map<String, ResolvedMetadata> resolveAll(
      final Collection<T> items,
      final Function<T, String> entityKeyExtractor,
      final AuditLogEntityType entityType,
      final AuditLogServices auditLogServices,
      final CamundaAuthentication authentication,
      final Function<T, String> creationTimestampFallback) {
    final Map<String, ResolvedMetadata> resolved = new HashMap<>();
    for (final T item : items) {
      final var entityKey = entityKeyExtractor.apply(item);
      try {
        final var query =
            AuditLogQuery.of(
                q ->
                    q.filter(
                            f ->
                                f.entityKeys(entityKey)
                                    .entityTypes(entityType.name())
                                    .results(SUCCESS.name()))
                        .sort(s -> s.timestamp().desc())
                        .page(p -> p.size(1)));
        final var latestAuditLog =
            auditLogServices.search(query, authentication).items().stream().findFirst();
        if (latestAuditLog.isPresent()) {
          final var auditLog = latestAuditLog.get();
          resolved.put(
              entityKey,
              new ResolvedMetadata(auditLog.actorId(), formatDate(auditLog.timestamp())));
        } else if (creationTimestampFallback != null) {
          // No audit log entry exists (e.g. creation itself is not an audited action for this
          // entity type). Initialize updatedAt to the entity's own creation time instead of
          // leaving it null, mirroring how Job#lastUpdateTime is set at creation.
          resolved.put(
              entityKey, new ResolvedMetadata(null, creationTimestampFallback.apply(item)));
        }
      } catch (final RuntimeException ignored) {
        // Update metadata is supplemental and must not fail the entity response.
      }
    }
    return resolved;
  }

  public record ResolvedMetadata(String updatedBy, String updatedAt) {
    public static final ResolvedMetadata EMPTY = new ResolvedMetadata(null, null);
  }
}
