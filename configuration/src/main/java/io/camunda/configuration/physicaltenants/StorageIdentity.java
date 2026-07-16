/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Rdbms;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.SecondaryStorageDatabase;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The minimal, location-determining tuple that decides whether two physical tenants would write
 * into the <em>same</em> secondary-storage location: {@code (type, connection, namespace, schema)}.
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
 *   <li>{@code schema} is the <em>Oracle-only</em> per-user schema key. On Oracle, schema == user,
 *       so two tenants isolated by distinct Oracle users on the same jdbc url (no distinct prefix,
 *       no schema in the url) are genuinely separate locations. Because the Oracle thin driver does
 *       not carry the schema in the url — unlike PostgreSQL's {@code currentSchema=…} — the
 *       connecting user is the only static signal of isolation. This field is populated
 *       <em>only</em> when the RDBMS {@code database-vendor-id} is explicitly {@code oracle}; it is
 *       always empty for every other vendor and for ES/OS, so distinct users never falsely isolate
 *       tenants on, e.g., PostgreSQL/MySQL (where the user does not partition storage).
 * </ul>
 *
 * <p>Documented limitations of the Oracle schema key (not enforced here):
 *
 * <ul>
 *   <li>Quoted, case-sensitive Oracle identifiers are not modelled — the user is folded to upper
 *       case, matching Oracle's default unquoted-identifier behaviour.
 *   <li>A {@code currentSchema=…} in the Oracle jdbc url overriding the connecting user is not
 *       detected; configure a distinct table prefix in that case.
 * </ul>
 *
 * @param type the secondary-storage type
 * @param connection the normalized, sorted connection url(s)
 * @param namespace the normalized within-database partition (never {@code null}; defaults to empty)
 * @param schema the normalized Oracle-only per-user schema (never {@code null}; empty for every
 *     other vendor and for ES/OS)
 */
@NullMarked
record StorageIdentity(
    SecondaryStorageType type, List<String> connection, String namespace, String schema) {

  private static final String ORACLE_VENDOR_ID = "oracle";

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
              secondaryStorage.getElasticsearch().getIndexPrefix(),
              "");
      case opensearch ->
          identityOf(
              type,
              secondaryStorage.getOpensearch(),
              secondaryStorage.getOpensearch().getIndexPrefix(),
              "");
      case rdbms -> {
        final Rdbms rdbms = secondaryStorage.getRdbms();
        yield identityOf(type, rdbms, rdbms.getPrefix(), oracleSchemaOf(rdbms));
      }
    };
  }

  private static StorageIdentity identityOf(
      final SecondaryStorageType type,
      final SecondaryStorageDatabase<?> database,
      final @Nullable String namespace,
      final String schema) {
    return new StorageIdentity(type, connectionOf(database), normalizeNamespace(namespace), schema);
  }

  /**
   * The Oracle schema key — the connecting user, which on Oracle <em>is</em> the schema. Returns an
   * empty string (no distinction) unless {@code database-vendor-id} is explicitly {@code oracle},
   * keeping distinct DB users from falsely isolating tenants on vendors where the user does not
   * partition storage. The user is trimmed and folded to upper case, matching Oracle's default
   * unquoted-identifier semantics; a blank user yields no distinction.
   */
  private static String oracleSchemaOf(final Rdbms rdbms) {
    final String vendorId = rdbms.getDatabaseVendorId();
    if (vendorId == null || !ORACLE_VENDOR_ID.equals(vendorId.trim().toLowerCase(Locale.ROOT))) {
      return "";
    }
    final String username = rdbms.getUsername();
    return username == null ? "" : username.trim().toUpperCase(Locale.ROOT);
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
    final String base =
        String.format("type=%s, connection=%s, namespace='%s'", type, connectionText, namespace);
    // the schema key only ever has a value on Oracle; omit it otherwise to keep messages terse
    return schema.isEmpty() ? base : base + String.format(", schema='%s'", schema);
  }

  /** Whether this identity is an RDBMS location (used to tailor collision-hint messaging). */
  boolean isRdbms() {
    return type == SecondaryStorageType.rdbms;
  }
}
