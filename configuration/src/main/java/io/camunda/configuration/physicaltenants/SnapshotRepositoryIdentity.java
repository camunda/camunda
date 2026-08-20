/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The identity of the Elasticsearch/OpenSearch snapshot repository a document-based physical tenant
 * writes its history backups into: {@code (type, connection, repositoryName)}.
 *
 * <p>Like {@link RetentionPolicyIdentity}, and unlike {@link StorageIdentity}, the index prefix is
 * deliberately <em>absent</em>: a snapshot repository is a cluster-global named object ({@code
 * _snapshot/<name>}), not scoped to any index prefix. Two tenants that share a cluster but use
 * distinct index prefixes — the explicitly-allowed "shared cluster, distinct prefix" setup under
 * {@link SecondaryStorageIsolationValidation} — nevertheless write into the same repository if they
 * resolve to the same {@code (type, connection, repositoryName)}.
 *
 * <p>The {@code connection} is normalized by {@link StorageIdentity#connectionOf} so every rule
 * agrees on what "the same cluster" means. The {@code repositoryName} is only trimmed —
 * <em>not</em> lowercased — because Elasticsearch and OpenSearch repository names are
 * case-sensitive. {@code type} discriminates Elasticsearch from OpenSearch, mirroring {@link
 * StorageIdentity}.
 *
 * @param type the document-based secondary-storage type (elasticsearch or opensearch)
 * @param connection the normalized, sorted connection url(s)
 * @param repositoryName the trimmed snapshot-repository name
 */
@NullMarked
record SnapshotRepositoryIdentity(
    SecondaryStorageType type, List<String> connection, String repositoryName) {

  /**
   * Extracts the snapshot-repository identity of a document-based tenant, or {@code null} when no
   * repository is configured — such a tenant takes no snapshots at all (its backup endpoints reject
   * every request at runtime) and therefore cannot collide with anything.
   */
  static @Nullable SnapshotRepositoryIdentity of(
      final SecondaryStorageType type, final DocumentBasedSecondaryStorageDatabase database) {
    final String repositoryName = database.getBackup().getRepositoryName();
    if (repositoryName == null || repositoryName.isBlank()) {
      return null;
    }
    return new SnapshotRepositoryIdentity(
        type, StorageIdentity.connectionOf(database), repositoryName.trim());
  }

  /** A human-readable rendering of this identity for error messages. */
  String describe() {
    final String connectionText =
        connection.size() == 1 ? connection.get(0) : connection.toString();
    return String.format(
        "type=%s, connection=%s, repositoryName='%s'", type, connectionText, repositoryName);
  }
}
