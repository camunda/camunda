/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.SecondaryStorageDatabase;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The minimal, location-determining tuple that decides whether two physical tenants would write
 * into the <em>same</em> secondary-storage location: {@code (type, connection, namespace)}.
 *
 * <p>Two tenants resolving to equal {@code StorageIdentity} values collide — they would silently
 * double-write into the same indices/tables. The identity is deliberately minimal: adding
 * non-location fields (cluster name, credentials) would let an incidental difference hide a real
 * collision. Detection is therefore <em>best-effort and static</em> — it catches identical and
 * near-identical configuration, not DNS aliases or distinct URLs that resolve to the same cluster.
 * In particular, two tenants whose multi-url {@code urls} lists only <em>partially</em> overlap (a
 * shared node, but not identical lists) produce different identities and are <em>not</em> flagged,
 * even though they would write into the same cluster.
 *
 * <ul>
 *   <li>{@code type} discriminates: two tenants on the same url collide only if they use the same
 *       {@link SecondaryStorageType} (Elasticsearch vs OpenSearch are distinct locations).
 *   <li>{@code connection} is the normalized url(s): Elasticsearch/OpenSearch endpoint(s), or the
 *       RDBMS jdbc url. Normalization is light and best-effort (trim, strip a trailing slash,
 *       lowercase, sort the list).
 *   <li>{@code namespace} is the within-database partition: index prefix (ES/OS) or table prefix
 *       (RDBMS). Same url + different namespace is the explicitly-allowed "shared cluster, distinct
 *       prefix" setup.
 * </ul>
 *
 * @param type the secondary-storage type
 * @param connection the normalized, sorted connection url(s)
 * @param namespace the normalized within-database partition (never {@code null}; defaults to empty)
 */
@NullMarked
record StorageIdentity(SecondaryStorageType type, List<String> connection, String namespace) {

  /**
   * Extracts the storage identity of a resolved tenant, or {@code null} when the tenant has no
   * secondary storage ({@code type=none}) and therefore can never collide.
   */
  static @Nullable StorageIdentity of(final Camunda camunda) {
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    // route through the getter: it applies the legacy-property fallback
    final SecondaryStorageType type = secondaryStorage.getType();
    return switch (type) {
      case none -> null;
      case elasticsearch ->
          identityOf(
              type,
              secondaryStorage.getElasticsearch(),
              secondaryStorage.getElasticsearch().getIndexPrefix());
      case opensearch ->
          identityOf(
              type,
              secondaryStorage.getOpensearch(),
              secondaryStorage.getOpensearch().getIndexPrefix());
      case rdbms ->
          identityOf(type, secondaryStorage.getRdbms(), secondaryStorage.getRdbms().getPrefix());
    };
  }

  private static StorageIdentity identityOf(
      final SecondaryStorageType type,
      final SecondaryStorageDatabase<?> database,
      final @Nullable String namespace) {
    return new StorageIdentity(type, connectionOf(database), normalizeNamespace(namespace));
  }

  /**
   * The connection is the {@code urls} list when configured, otherwise the single {@code url}. Both
   * live on {@link SecondaryStorageDatabase} and are mutually exclusive (enforced at getter time).
   */
  private static List<String> connectionOf(final SecondaryStorageDatabase<?> database) {
    final List<String> urls = database.getUrls();
    final List<String> raw = (urls != null && !urls.isEmpty()) ? urls : List.of(database.getUrl());
    return raw.stream().map(StorageIdentity::normalizeUrl).sorted().toList();
  }

  private static String normalizeUrl(final String url) {
    String normalized = url.trim();
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized.toLowerCase();
  }

  private static String normalizeNamespace(final @Nullable String namespace) {
    // null (RDBMS default) and "" (ES/OS default) are both "no prefix" and must collide
    return namespace == null ? "" : namespace.trim();
  }

  /** A human-readable rendering of this identity for error messages. */
  String describe() {
    final String connectionText =
        connection.size() == 1 ? connection.get(0) : connection.toString();
    return String.format("type=%s, connection=%s, namespace='%s'", type, connectionText, namespace);
  }
}
