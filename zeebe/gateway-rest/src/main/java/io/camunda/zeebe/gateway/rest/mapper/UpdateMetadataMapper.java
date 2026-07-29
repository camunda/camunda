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
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class UpdateMetadataMapper {

  private UpdateMetadataMapper() {}

  public static <T> void addUpdateMetadata(
      final T item,
      final Function<T, String> entityKeyExtractor,
      final AuditLogEntityType entityType,
      final AuditLogServices auditLogServices,
      final CamundaAuthentication authentication,
      final BiConsumer<T, String> updatedBySetter,
      final BiConsumer<T, String> updatedAtSetter) {
    addUpdateMetadata(
        List.of(item),
        entityKeyExtractor,
        entityType,
        auditLogServices,
        authentication,
        updatedBySetter,
        updatedAtSetter,
        null);
  }

  /**
   * Same as {@link #addUpdateMetadata(Object, Function, AuditLogEntityType, AuditLogServices,
   * CamundaAuthentication, BiConsumer, BiConsumer)}, but falls back to {@code
   * creationTimestampFallback} for {@code updatedAt} when the entity has no audit log entry at all
   * (e.g. it was never explicitly updated after creation, and its creation is not itself an audited
   * action).
   */
  public static <T> void addUpdateMetadata(
      final T item,
      final Function<T, String> entityKeyExtractor,
      final AuditLogEntityType entityType,
      final AuditLogServices auditLogServices,
      final CamundaAuthentication authentication,
      final BiConsumer<T, String> updatedBySetter,
      final BiConsumer<T, String> updatedAtSetter,
      final Function<T, String> creationTimestampFallback) {
    addUpdateMetadata(
        List.of(item),
        entityKeyExtractor,
        entityType,
        auditLogServices,
        authentication,
        updatedBySetter,
        updatedAtSetter,
        creationTimestampFallback);
  }

  public static <T> void addUpdateMetadata(
      final Collection<T> items,
      final Function<T, String> entityKeyExtractor,
      final AuditLogEntityType entityType,
      final AuditLogServices auditLogServices,
      final CamundaAuthentication authentication,
      final BiConsumer<T, String> updatedBySetter,
      final BiConsumer<T, String> updatedAtSetter) {
    addUpdateMetadata(
        items,
        entityKeyExtractor,
        entityType,
        auditLogServices,
        authentication,
        updatedBySetter,
        updatedAtSetter,
        null);
  }

  /**
   * Resolves the metadata for a whole page of items.
   *
   * <p>KNOWN COST: this issues <em>one audit log query per item</em>, so a search endpoint
   * returning the default page of 100 items performs 100 additional queries. Callers must therefore
   * only invoke this while {@code camunda.rest.update-metadata.enabled} is set, which is off by
   * default.
   *
   * <p>The queries are not batched even though {@code AuditLogFilter#entityKeys} accepts several
   * keys, because selecting the <em>latest</em> entry per key relies on {@code sort(timestamp
   * desc)} combined with {@code size(1)}. The audit log search exposes no per-group top-hits or
   * aggregation, so a single bounded query cannot guarantee one row per key: an entity with many
   * entries can crowd every other key out of the page. Batching correctly would mean paging until
   * every key is resolved, which is deliberately left out of scope here.
   */
  public static <T> void addUpdateMetadata(
      final Collection<T> items,
      final Function<T, String> entityKeyExtractor,
      final AuditLogEntityType entityType,
      final AuditLogServices auditLogServices,
      final CamundaAuthentication authentication,
      final BiConsumer<T, String> updatedBySetter,
      final BiConsumer<T, String> updatedAtSetter,
      final Function<T, String> creationTimestampFallback) {
    for (final T item : items) {
      try {
        final var entityKey = entityKeyExtractor.apply(item);
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
          if (auditLog.actorId() != null) {
            updatedBySetter.accept(item, auditLog.actorId());
          }
          updatedAtSetter.accept(item, formatDate(auditLog.timestamp()));
        } else if (creationTimestampFallback != null) {
          // No audit log entry exists (e.g. creation itself is not an audited action for this
          // entity type). Initialize updatedAt to the entity's own creation time instead of
          // leaving it null, mirroring how Job#lastUpdateTime is set at creation.
          updatedAtSetter.accept(item, creationTimestampFallback.apply(item));
        }
      } catch (final RuntimeException ignored) {
        // Update metadata is supplemental and must not fail the entity response.
      }
    }
  }
}
